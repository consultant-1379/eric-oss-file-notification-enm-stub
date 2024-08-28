/*******************************************************************************
 * COPYRIGHT Ericsson 2021
 *
 *
 *
 * The copyright to the computer program(s) herein is the property of
 *
 * Ericsson Inc. The programs may be used and/or copied only with written
 *
 * permission from Ericsson Inc. or in accordance with the terms and
 *
 * conditions stipulated in the agreement/contract under which the
 *
 * program(s) have been supplied.
 ******************************************************************************/

package com.ericsson.oss.adc.services;

import static com.ericsson.oss.adc.utils.Utilities.UNIX_PATH_SEPARATOR;
import static com.ericsson.oss.adc.utils.Utilities.toUnixPathSeparator;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ericsson.oss.adc.exceptions.NoSftpConnectionException;
import com.ericsson.oss.adc.handler.SymlinkResult;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Service class to do SFTP work
 */
@Service
public class SftpService {

    private static final Logger LOG = LoggerFactory.getLogger(SftpService.class);

    private final Counter numSftpFilesRenamed;

    private final Counter numSftpFilesUploaded;

    private ChannelSftp sftpConnection;

    /**
     * Constructor to build the metrics
     *
     * @param meterRegistry The meter registry
     */
    public SftpService(final MeterRegistry meterRegistry) {
        numSftpFilesRenamed = meterRegistry.counter("eric.oss.file.notification.enm.stub:sftp.files.renamed");
        numSftpFilesUploaded = meterRegistry.counter("eric.oss.file.notification.enm.stub:sftp.files.uploaded");
    }

    /**
     * Returns a true if the sftp connection is not a null, false if it is a null
     *
     * @return if the sftpConnection is a null
     */
    public boolean sftpConnectionPresent() {
        if (sftpConnection != null) {
            LOG.debug("SFTP Connection is available, isConnected = '{}'", sftpConnection.isConnected());
            return sftpConnection.isConnected();
        }
        LOG.error("ERROR: No SFTP Connection available,  sftpConnection is '{}'", sftpConnection);
        return false;
    }

    private ChannelSftp getSftpConnection() throws NoSftpConnectionException {
        // get the ChannelSftp instance if present, else throw NoSftpConnectionException
        if (sftpConnectionPresent()){
            return sftpConnection;
        }
        throw new NoSftpConnectionException("NoSftpConnectionException: SFTP Connection not present");
    }

    /**
     * Changes the permissions of one or several remote files.
     *
     * @param permissions The new permission pattern. This may be modified by a current mask before being applied.
     * @param path A glob pattern of the files to be reowned, relative to the current remote directory.
     */
    public void chmodRemoteFile(final String permissions, String path) {
        path = toUnixPathSeparator(path);
        LOG.info("Performing chmod '{}' on '{}'", permissions, path);
        try {
            getSftpConnection().chmod(Integer.parseInt(permissions, 8), path);
        } catch (final SftpException | NoSftpConnectionException exception) {
            LOG.error("{}: ERROR changing file permissons '{}' on '{}'", exception.getMessage(), permissions, path);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error: Exception caught while changing file permissons: ", exception);
            }
        }
    }

    /**
     * Change directory to specified remote directory
     *
     * @param remotePath Full path of remote directory
     */
    public void cdToRemotePath(String remotePath) throws SftpException, NoSftpConnectionException {
        // do NOT catch here
        remotePath = toUnixPathSeparator(remotePath);
        LOG.debug("Changing directory to '{}'", remotePath);
        getSftpConnection().cd(remotePath);
    }

    private int cdToHighestCommonDirAndGetStartingIndexForDirCreation(
                                                                      final List<String> remotePathDirs, final List<String> existingDirs)
                                                                              throws SftpException, NoSftpConnectionException {
        // move to the highest common dir and find starting index to start creating new directories from
        int startingRemotePathDirIndex = 0;
        for (final String existingDir: existingDirs) {
            for (int remotePathDirIndex = startingRemotePathDirIndex;
                    remotePathDirIndex < remotePathDirs.size(); remotePathDirIndex++) {

                final String remotePathDir = remotePathDirs.get(remotePathDirIndex);
                if (!existingDir.equals(remotePathDir)) {
                    break;
                }
                cdToRemotePath(existingDir);
            }
            startingRemotePathDirIndex++;
        }
        return startingRemotePathDirIndex;
    }

    private void createRequiredRemoteDirs(final List<String> remotePathDirs, final int remotePathDirStartingIndex, final String permissions)
            throws SftpException, NoSftpConnectionException {
        // create missing directories with the specified permissions
        for (int remotePathDirIndex = remotePathDirStartingIndex;
                remotePathDirIndex < remotePathDirs.size(); remotePathDirIndex++) {

            final String directory = remotePathDirs.get(remotePathDirIndex);
            if (directory.equalsIgnoreCase("")) {
                continue;
            }
            try {
                cdToRemotePath(directory);
            } catch (final SftpException | NoSftpConnectionException exception) {
                // No such folder yet so create it and move to it for the next dir to be possibly created
                LOG.info("{}: Remote directory '{}' does not exist, creating...",
                        exception.getMessage() ,directory);
                getSftpConnection().mkdir(directory);
                chmodRemoteFile(permissions, directory);
                cdToRemotePath(directory);
            }
        }
    }

    private List<String> getPathComponents(String path) {
        path = toUnixPathSeparator(path);
        final List<String> components = new ArrayList<>();
        Paths.get(path).iterator().forEachRemaining(e -> components.add(String.valueOf(e)));
        return components;
    }

    /**
     * Create a directory at remote path with the given permissions.
     *
     * @param remotePath path of the directory to create.
     * @param permissions permissions for the directory.
     */
    public void mkdirRemotePath(String remotePath, final String permissions) {
        remotePath = toUnixPathSeparator(remotePath);
        LOG.info("Creating remote directory '{}'", remotePath);
        try {
            final String originalWorkingDir = getSftpConnection().pwd();

            // go to root if path provided is absolute
            if (remotePath.startsWith(UNIX_PATH_SEPARATOR)) {
                cdToRemotePath(UNIX_PATH_SEPARATOR);
            }

            // split path into a List of the path components
            final List<String> remotePathDirs = getPathComponents(remotePath);
            final List<String> existingDirs = getPathComponents(originalWorkingDir);

            final int remotePathDirStartingIndex =
                    cdToHighestCommonDirAndGetStartingIndexForDirCreation(remotePathDirs, existingDirs);
            createRequiredRemoteDirs(remotePathDirs, remotePathDirStartingIndex, permissions);

            cdToRemotePath(originalWorkingDir); //reset to the dir you were in already
        } catch (final SftpException | NoSftpConnectionException exception) {
            LOG.error("{}: Error creating remote directory '{}'", exception.getMessage(), remotePath);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error: Exception caught while creating remote directory: ", exception);
            }
        }
    }

    /**
     * Uploads a file, maintaining the directory structure, from local path to the remote path and sets the
     * permissions of the file.
     *
     * @param localPath The local source file name, absolute or relative to the current local directory.
     * @param remotePath The remote destination file name, absolute or relative to the current remote
     *                   directory (user home).
     * @param permissions The new permission pattern. This may be modified by a current mask before being applied.
     */
    public boolean upload(String localPath, String remotePath, final String permissions) {
        localPath = toUnixPathSeparator(localPath);
        remotePath = toUnixPathSeparator(remotePath);
        LOG.info("Uploading '{}' to '{}'", localPath, remotePath);
        try {
            final String remotePathBase = toUnixPathSeparator(new File(remotePath).getParent());
            mkdirRemotePath(remotePathBase, permissions);
            getSftpConnection().put(localPath, remotePath);
            chmodRemoteFile(permissions, remotePath);
            numSftpFilesUploaded.increment();
            return true;
        } catch (final SftpException | NoSftpConnectionException exception) {
            LOG.error("{}: Error uploading file from '{}' to '{}'", exception.getMessage(), localPath, remotePath);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Exception caught while uploading file: ", exception);
            }
            return false;
        }
    }

    /**
     * Creates a symlink between remote files, maintaining the directory structure and sets the
     * permissions of the file.
     *
     * @param sourceFile
     *            The remote file name, absolute or relative to the current local directory.
     * @param linkedFile
     *            The remote destination file name, absolute or relative to the current remote
     *            directory (user home).
     * @param permissions
     *            The new permission pattern. This may be modified by a current mask before being applied.
     *
     * @return true, if successful
     */
    public SymlinkResult symlink(String sourceFile, String linkedFile, final String permissions) {
        sourceFile = toUnixPathSeparator(sourceFile);
        linkedFile = toUnixPathSeparator(linkedFile);
        LOG.info("Creating symlink from '{}' to '{}'", sourceFile, linkedFile);
        // IN manual mode, the ROP generation may be triggered, multiple times in the same ROP.
        // No need to re-create the symlink if its already there.
        if (doesSymLinkExists(linkedFile)) {
            LOG.info("Symlink Already exists, will not re-create");
            return SymlinkResult.EXIST;
        }
        try {
            final String parentDirectory = String.valueOf(Paths.get(linkedFile).getParent());
            mkdirRemotePath(parentDirectory, permissions);
            getSftpConnection().symlink(sourceFile, linkedFile); // no need to chmod, sourceFile perms apply
            numSftpFilesRenamed.increment();
            LOG.info("Symlink created successfully");
            return SymlinkResult.SUCCESS;
        } catch (final SftpException sftpException) {
            LOG.error("{} {}: Error, Sftp Exception when creating symlink from '{}' to '{}'", sftpException.getMessage(), sftpException.id,
                    sourceFile,
                    linkedFile);
            logException(sftpException);
        } catch (final NoSftpConnectionException noSftpConnectionException) {
            LOG.error("{} : Error, No Sftp Connection Exception when creating symlink from '{}' to '{}'", noSftpConnectionException.getMessage(),
                    sourceFile,
                    linkedFile);
            logException(noSftpConnectionException);
        }
        LOG.error("Symlink NOT created, error creating symlink");
        return SymlinkResult.FAILED;
    }


    /**
     * Return a List of {@link ChannelSftp.LsEntry} of everything in the specified remote path
     *
     * @param remotePath The remote path
     * @return A Vector of ChannelSftp.LsEntry
     */
    public List<ChannelSftp.LsEntry> listRemotePath(String remotePath) {
        remotePath = toUnixPathSeparator(remotePath);
        LOG.info("Getting list of items on remote path '{}'", remotePath);
        List<ChannelSftp.LsEntry> list = new ArrayList<>();
        try {
            list = new ArrayList<>(getSftpConnection().ls(remotePath));
        } catch (final SftpException | NoSftpConnectionException exception) {
            LOG.error("{}: Error getting list of items on remote path '{}'", exception.getMessage(), remotePath);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error: Exception caught while getting list of items on remote path  ", exception);
            }
        }
        return list;
    }

    /**
     * Return a String List of file paths of files in the specified remote path
     *
     * @param remotePath The remote path.
     * @return String List of file paths.
     */
    public List<String> listRemoteFilePaths(String remotePath){
        remotePath = toUnixPathSeparator(remotePath);
        LOG.info("Getting list of file paths on remote path '{}'", remotePath);
        return listDirectoryRecursive(remotePath, new ArrayList<>());
    }

    /**
     * Remove a file on the remote server
     *
     * @param remotePath The remote path.
     */
    public void rm(String remotePath){
        remotePath = toUnixPathSeparator(remotePath);
        LOG.info("Removing remote file '{}'", remotePath);
        try {
            sftpConnection.rm(remotePath);
        } catch (final SftpException e) {
            LOG.error("Error removing file {} with logs: {}", remotePath, e);
        }
    }

    /**
     * Does sym link exists.
     * check the 'stats' of the remote symlink. If the link does not exist, it throws exception, so
     * symlink does not exist.
     *
     * @param pathToSymLink
     *            the path to sym link
     *
     * @return true, if successful
     */
    private boolean doesSymLinkExists(final String pathToSymLink) {
        SftpATTRS symLinkStats = null;
        try {
            symLinkStats = getSftpConnection().lstat(pathToSymLink);
        } catch (final SftpException sftpException) {
            if (sftpException.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            LOG.error("{} {}: Error, Sftp Exception when checking if symlink exists:'{}'", sftpException.getMessage(), sftpException.id,
                    pathToSymLink);
            logException(sftpException);
        } catch (final NoSftpConnectionException noSftpConnectionException) {
            LOG.error("{}: Error, No Sftp Connection Exception when checking if symlink exists:'{}'", noSftpConnectionException.getMessage(),
                    pathToSymLink);
            logException(noSftpConnectionException);
        }
        LOG.debug("SFTP_ATTRIBUTES {}: isSymLink {}:  {}", symLinkStats,
                (symLinkStats == null ? "null" : symLinkStats.isLink()), pathToSymLink);

        return symLinkStats != null && symLinkStats.isLink();
    }

    private void logException(final Exception exception) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Exception caught while creating symlink: ", exception);
        }
    }

    private List<String> listDirectoryRecursive(String remotePath, final List<String> list) {
        // gets absolute paths for every file under the given remotePath
        remotePath = toUnixPathSeparator(remotePath);
        if (!remotePath.endsWith(UNIX_PATH_SEPARATOR)) {
            remotePath += UNIX_PATH_SEPARATOR;
        }
        final List<ChannelSftp.LsEntry> files = new ArrayList<>(listRemotePath(remotePath));

        for (final ChannelSftp.LsEntry entry : files) {
            if (!entry.getAttrs().isDir()) {
                list.add(remotePath + entry.getFilename());
            } else {
                if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    listDirectoryRecursive(remotePath + entry.getFilename(), list);
                }
            }
        }
        return list;
    }

    public void setSftpConnection(final ChannelSftp sftpConnection) {
        this.sftpConnection = sftpConnection;
    }

}
