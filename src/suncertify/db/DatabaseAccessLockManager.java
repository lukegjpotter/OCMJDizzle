package suncertify.db;

import java.util.HashMap;
import java.util.Map;

/**
 * This singleton class handles the locking and unlocking of the database
 * records.
 *
 * @author Luke GJ Potter
 * @since 27/03/2014
 */
class DatabaseAccessLockManager {

    // Singleton instance of this {@code DatabaseAccessLockManager} class.
    private static DatabaseAccessLockManager databaseAccessLockManager;
    // A map to record which records (key) are locked by which thread (value).
    private final Map<Long, Long> lockedMap = new HashMap<>();

    /**
     * Private constructor for the singleton.
     */
    private DatabaseAccessLockManager() {
    }

    /**
     * This method returns the single instance of the
     * {@code DatabaseAccessLockManager} class.
     *
     * @return the instance to the calling class
     */
    public static DatabaseAccessLockManager getInstance() {

        if (databaseAccessLockManager == null) {
            databaseAccessLockManager = new DatabaseAccessLockManager();
        }

        return databaseAccessLockManager;
    }

    /**
     * Locks a record so that it can only be updated or deleted by this client.
     * Returned value is a cookie that must be used when the record is unlocked,
     * updated, or deleted. If the specified record is already locked by a
     * different client, the current thread gives up the CPU and consumes no CPU
     * cycles until the record is unlocked.
     *
     * @param recNo the record number to lock
     * @return A long representing the lock's owner's id.
     * @throws RecordNotFoundException
     */
    public synchronized long lock(long recNo) throws RecordNotFoundException {

        DatabaseAccessCrudOperations.isValidRecordNumber(recNo);

        return lockRecordWhenCreatingNewRecord(recNo);
    }

    /**
     * Releases the lock on a record. Cookie must be the cookie returned when
     * the record was locked; otherwise throws {@code SecurityException}.
     *
     * @param recNo  the record number to unlock
     * @param cookie the cookie to unlock the record with
     * @throws SecurityException
     */
    public synchronized void unlock(long recNo, long cookie)
            throws SecurityException {

        try {
            DatabaseAccessCrudOperations.isValidRecordNumber(recNo);
            unlockRecordWhenCreatingOrDeletingRecord(recNo, cookie);

        } catch (RecordNotFoundException e) {
            throw new SecurityException("Record " + recNo + " was not found.");
        }
    }

    /**
     * This method is used for locking a record number when creating a new
     * record.
     *
     * @param recordNumber the record number to lock
     * @return A long representing the lock's owner's id.
     * @throws RecordNotFoundException
     */
    public synchronized long lockRecordWhenCreatingNewRecord(long recordNumber)
            throws RecordNotFoundException {

        try {
            final long lockingCookie = Thread.currentThread().getId();

            while (isRecordLockedBySomeoneElse(recordNumber, lockingCookie)) {
                wait();
            }

            lockedMap.put(recordNumber, lockingCookie);
            return lockingCookie;

        } catch (InterruptedException interruptedException) {

            throw new RecordNotFoundException(
                    "Encountered a problem when trying to lock record: "
                            + recordNumber + ".\n"
                            + interruptedException.getMessage());
        }
    }

    /**
     * This method is for unlocking a record when creating or deleting a new
     * record.
     *
     * @param recordNumber  the record number to unlock
     * @param lockingCookie the cookie to unlock the record with
     * @throws SecurityException
     */
    public synchronized void unlockRecordWhenCreatingOrDeletingRecord(
            long recordNumber, long lockingCookie) throws SecurityException {

        if (isRecordLockedByThisUser(recordNumber, lockingCookie)) {
            lockedMap.remove(recordNumber);
            notifyAll();
        } else {
            throw new SecurityException("Record "
                    + recordNumber
                    + " locked by another user.");
        }
    }

    /**
     * Checks if the {@code recordNumber} is present in the {@code lockedMap},
     * should the record be in the map, the value of the map's entry for the
     * record is compared to the {@code lockingCookie}.
     *
     * @param recordNumber  the record number to investigate
     * @param lockingCookie the cookie to investigate
     * @return True, if the record is locked by another cookie.
     * False, if the record is not locked.
     */
    public boolean isRecordLockedBySomeoneElse(
            long recordNumber, long lockingCookie) {

        return isRecordLocked(recordNumber)
                && getLockOwner(recordNumber) != lockingCookie;
    }

    /**
     * Checks if the {@code recordNumber} is present in the {@code lockedMap},
     * should the record be in the map, the value of the map's entry for the
     * record is compared to the {@code lockingCookie}.
     *
     * @param recordNumber  the record number to investigate
     * @param lockingCookie the cookie to investigate
     * @return True, if the record is locked by the cookie.
     * False, if the record is not locked by this cookie.
     */
    boolean isRecordLockedByThisUser(long recordNumber, long lockingCookie) {

        return isRecordLocked(recordNumber)
                && getLockOwner(recordNumber) == lockingCookie;
    }

    /**
     * Checks the {@code lockedMap} to see if {@code recordNumber} is present.
     *
     * @param recordNumber the record number to investigate
     * @return True, if the record is in the map of locked records.
     * False, if the record is not in the map of locked records.
     */
    boolean isRecordLocked(long recordNumber) {

        return lockedMap.containsKey(recordNumber);
    }

    /**
     * Gets the id number of the current owner of the lock on a record.
     *
     * @param recordNumber the record number to investigate
     * @return A long representing the lock's owner's id.
     */
    Long getLockOwner(long recordNumber) {

        return lockedMap.get(recordNumber);
    }
}