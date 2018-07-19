/*
 *      Copyright (C) 2012, 2016  higherfrequencytrading.com
 *      Copyright (C) 2016 Roman Leventov
 *
 *      This program is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU Lesser General Public License as published by
 *      the Free Software Foundation, either version 3 of the License.
 *
 *      This program is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU Lesser General Public License for more details.
 *
 *      You should have received a copy of the GNU Lesser General Public License
 *      along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.hash.impl.stage.entry;

import net.openhft.chronicle.hash.impl.VanillaChronicleHashHolder;
import net.openhft.chronicle.hash.impl.stage.hash.CheckOnEachPublicOperation;
import net.openhft.chronicle.hash.locks.InterProcessDeadLockException;
import net.openhft.chronicle.hash.locks.InterProcessLock;
import net.openhft.sg.StageRef;
import net.openhft.sg.Staged;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import static net.openhft.chronicle.hash.impl.LocalLockState.READ_LOCKED;
import static net.openhft.chronicle.hash.impl.LocalLockState.UPDATE_LOCKED;

@Staged
public class UpdateLock implements InterProcessLock {

    @StageRef
    VanillaChronicleHashHolder<?> hh;
    @StageRef
    CheckOnEachPublicOperation checkOnEachPublicOperation;
    @StageRef
    SegmentStages s;
    @StageRef
    HashEntryStages<?> entry;

    @Override
    public boolean isHeldByCurrentThread() {
        checkOnEachPublicOperation.checkOnEachLockOperation();
        return s.localLockState.update;
    }

    @Override
    public void lock() {
        checkOnEachPublicOperation.checkOnEachLockOperation();
        switch (s.localLockState) {
            case UNLOCKED:
                s.checkIterationContextNotLockedInThisThread();
                if (s.updateZero() && s.writeZero()) {
                    if (!s.readZero())
                        throw forbiddenUpdateLockWhenOuterContextReadLocked();
                    try {
                        s.segmentHeader.updateLock(s.segmentHeaderAddress);
                    } catch (InterProcessDeadLockException e) {
                        throw s.debugContextsAndLocks(e);
                    }
                }
                s.incrementUpdate();
                s.setLocalLockState(UPDATE_LOCKED);
                return;
            case READ_LOCKED:
                throw forbiddenUpgrade();
            case UPDATE_LOCKED:
            case WRITE_LOCKED:
                // do nothing
        }
    }

    /**
     * Non-static because after compilation it becomes inner class which forbids static methods
     */
    @NotNull
    private IllegalMonitorStateException forbiddenUpgrade() {
        return new IllegalMonitorStateException(
                hh.h().toIdentityString() + ": Cannot upgrade from read to update lock");
    }

    /**
     * Non-static because after compilation it becomes inner class which forbids static methods
     */
    @NotNull
    private IllegalStateException forbiddenUpdateLockWhenOuterContextReadLocked() {
        return new IllegalStateException(hh.h().toIdentityString() +
                ": Cannot acquire update lock, because outer context holds read lock. " +
                "In this case you should acquire update lock in the outer context up front");
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        checkOnEachPublicOperation.checkOnEachLockOperation();
        if (Thread.interrupted())
            throw new InterruptedException();
        switch (s.localLockState) {
            case UNLOCKED:
                s.checkIterationContextNotLockedInThisThread();
                if (s.updateZero() && s.writeZero()) {
                    if (!s.readZero())
                        throw forbiddenUpdateLockWhenOuterContextReadLocked();
                    try {
                        s.segmentHeader.updateLockInterruptibly(s.segmentHeaderAddress);
                    } catch (InterProcessDeadLockException e) {
                        throw s.debugContextsAndLocks(e);
                    }
                }
                s.incrementUpdate();
                s.setLocalLockState(UPDATE_LOCKED);
                return;
            case READ_LOCKED:
                throw forbiddenUpgrade();
            case UPDATE_LOCKED:
            case WRITE_LOCKED:
                // do nothing
        }
    }

    @Override
    public boolean tryLock() {
        checkOnEachPublicOperation.checkOnEachLockOperation();
        switch (s.localLockState) {
            case UNLOCKED:
                s.checkIterationContextNotLockedInThisThread();
                if (s.updateZero() && s.writeZero()) {
                    if (!s.readZero())
                        throw forbiddenUpdateLockWhenOuterContextReadLocked();
                    if (s.segmentHeader.tryUpdateLock(s.segmentHeaderAddress)) {
                        s.incrementUpdate();
                        s.setLocalLockState(UPDATE_LOCKED);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    s.incrementUpdate();
                    s.setLocalLockState(UPDATE_LOCKED);
                    return true;
                }
            case READ_LOCKED:
                throw forbiddenUpgrade();
            case UPDATE_LOCKED:
            case WRITE_LOCKED:
                return true;
            default:
                throw new IllegalStateException(hh.h().toIdentityString() +
                        ": unexpected localLockState=" + s.localLockState);
        }
    }

    @Override
    public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
        checkOnEachPublicOperation.checkOnEachLockOperation();
        if (Thread.interrupted())
            throw new InterruptedException();
        switch (s.localLockState) {
            case UNLOCKED:
                s.checkIterationContextNotLockedInThisThread();
                if (s.updateZero() && s.writeZero()) {
                    if (!s.readZero())
                        throw forbiddenUpdateLockWhenOuterContextReadLocked();
                    if (s.segmentHeader.tryUpdateLock(s.segmentHeaderAddress, time, unit)) {
                        s.incrementUpdate();
                        s.setLocalLockState(UPDATE_LOCKED);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    s.incrementUpdate();
                    s.setLocalLockState(UPDATE_LOCKED);
                    return true;
                }
            case READ_LOCKED:
                throw forbiddenUpgrade();
            case UPDATE_LOCKED:
            case WRITE_LOCKED:
                return true;
            default:
                throw new IllegalStateException(hh.h().toIdentityString() +
                        ": unexpected localLockState=" + s.localLockState);
        }
    }

    @Override
    public void unlock() {
        checkOnEachPublicOperation.checkOnEachLockOperation();
        switch (s.localLockState) {
            case UNLOCKED:
            case READ_LOCKED:
                return;
            case UPDATE_LOCKED:
                entry.closeDelayedUpdateChecksum();
                if (s.decrementUpdate() == 0 && s.writeZero()) {
                    s.segmentHeader.downgradeUpdateToReadLock(s.segmentHeaderAddress);
                }
                break;
            case WRITE_LOCKED:
                entry.closeDelayedUpdateChecksum();
                if (s.decrementWrite() == 0) {
                    if (!s.updateZero()) {
                        s.segmentHeader.downgradeWriteToUpdateLock(s.segmentHeaderAddress);
                    } else {
                        s.segmentHeader.downgradeWriteToReadLock(s.segmentHeaderAddress);
                    }
                }
        }
        s.incrementRead();
        s.setLocalLockState(READ_LOCKED);
    }
}
