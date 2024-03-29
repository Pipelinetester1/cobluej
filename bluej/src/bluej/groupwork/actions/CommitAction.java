/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.groupwork.actions;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import javax.swing.AbstractAction;

import bluej.Config;
import bluej.groupwork.StatusHandle;
import bluej.groupwork.TeamStatusInfo;
import bluej.groupwork.TeamUtils;
import bluej.groupwork.TeamworkCommand;
import bluej.groupwork.TeamworkCommandResult;
import bluej.groupwork.ui.CommitCommentsFrame;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.utility.SwingWorker;


/**
 * An action to do an actual commit.
 * 
 * <p>This action should not be enabled until the following methods have
 * been called:
 * 
 * <ul>
 * <li>setNewFiles()
 * <li>setDeletedFiles()
 * <li>setFiles()
 * <li>setStatusHandle()
 * </ul>
 * 
 * @author Kasper
 * @version $Id: CommitAction.java 6215 2009-03-30 13:28:25Z polle $
 */
public class CommitAction extends AbstractAction
{
    private Set<File> newFiles; // which files are new files
    private Set<File> deletedFiles; // which files are to be removed
    private Set<File> files; // files to commit (includes both of above)
    private CommitCommentsFrame commitCommentsFrame;
    private StatusHandle statusHandle;
    
    private CommitWorker worker;
    
    public CommitAction(CommitCommentsFrame frame)
    {
        super(Config.getString("team.commit"));
        commitCommentsFrame = frame; 
    }
    
    /**
     * Set the files which are new, that is, which aren't presently under
     * version management and which need to be added.
     */
    public void setNewFiles(Set<File> newFiles)
    {
        this.newFiles = newFiles;
    }
    
    /**
     * Set the files which have been deleted locally, and the deletion
     * needs to be propagated to the repository.
     */
    public void setDeletedFiles(Set<File> deletedFiles)
    {
        this.deletedFiles = deletedFiles;
    }
    
    /**
     * Set all files which are to be committed. This should include both
     * the new files and the deleted files, as well as any other files
     * which have been locally modified and need to be committed.
     */
    public void setFiles(Set<File> files)
    {
        this.files = files;
    }
    
    /**
     * Set the status handle to use in order to perform the commit operation.
     */
    public void setStatusHandle(StatusHandle statusHandle)
    {
        this.statusHandle = statusHandle;
    }
    
    /* (non-Javadoc)
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(ActionEvent event) 
    {
        Project project = commitCommentsFrame.getProject();
        
        if (project != null) {
            commitCommentsFrame.startProgress();
            PkgMgrFrame.displayMessage(project, Config.getString("team.commit.statusMessage"));
            project.saveAllEditors();
            setEnabled(false);
            
            //doCommit(project);
            worker = new CommitWorker(project);
            worker.start();
        }
    }
    
    /**
     * Cancel the commit, if it is running.
     */
    public void cancel()
    {
        setEnabled(true);
        if(worker != null) {
            worker.abort();
            worker = null;
        }
    }

    /**
     * Worker thread to perform commit operation
     * 
     * @author Davin McCall
     */
    private class CommitWorker extends SwingWorker
    {
        private TeamworkCommand command;
        private TeamworkCommandResult result;
        private boolean aborted;
        
        public CommitWorker(Project project)
        {
            String comment = commitCommentsFrame.getComment();
            Set<TeamStatusInfo> forceFiles = new HashSet<TeamStatusInfo>();
            
            //last step before committing is to add in modified diagram 
            //layouts if selected in commit comments dialog
            if(commitCommentsFrame.includeLayout()) {
                forceFiles = commitCommentsFrame.getChangedLayoutInfo();
                files.addAll(commitCommentsFrame.getChangedLayoutFiles());
            }

            Set<File> binFiles = TeamUtils.extractBinaryFilesFromSet(newFiles);

            command = statusHandle.commitAll(newFiles, binFiles, deletedFiles, files,
                    forceFiles, comment);
        }
        
        public Object construct()
        {
            result = command.getResult();
            return result;
        }
        
        public void abort()
        {
            command.cancel();
            aborted = true;
        }
        
        public void finished()
        {
            final Project project = commitCommentsFrame.getProject();
            
            if (! aborted) {
                commitCommentsFrame.stopProgress();
                if (! result.isError() && ! result.wasAborted()) {
                    EventQueue.invokeLater(new Runnable() {
                        public void run() {
                            PkgMgrFrame.displayMessage(project, Config.getString("team.commit.statusDone"));
                        }
                    });
                }
            }

            TeamUtils.handleServerResponse(result, commitCommentsFrame);
            
            if (! aborted) {
                setEnabled(true);
                commitCommentsFrame.setVisible(false);
            }
        }
    }
}
