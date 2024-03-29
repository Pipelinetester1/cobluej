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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import bluej.Config;
import bluej.groupwork.*;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;

/**
 * An action to perform an import into a repository, i.e. to share a project.
 * 
 * @author Kasper
 * @version $Id: ImportAction.java 6215 2009-03-30 13:28:25Z polle $
 */
public class ImportAction extends TeamAction 
{
	public ImportAction()
    {
        super("team.import");
    }
	
    /* (non-Javadoc)
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(PkgMgrFrame pmf)
    {
        Project project = pmf.getProject();
	    
        if (project == null) {
            return;
        }
	    
        doImport(pmf, project);
    }

    private void doImport(final PkgMgrFrame pmf, final Project project)
    {
        // The team settings controller is not initially associated with the
        // project, so you can still modify the repository location
        final TeamSettingsController tsc = new TeamSettingsController(project.getProjectDir());
        final Repository repository = tsc.getRepository(true);
        
        if (repository == null) {
            // user cancelled
            return;
        }

        project.saveAllGraphLayout();
        setStatus(Config.getString("team.sharing"));
        startProgressBar(); 
        
        Thread thread = new Thread() {
            
            TeamworkCommandResult result = null;
            
            public void run()
            {
                // boolean resetStatus = true;
                TeamworkCommand command = repository.shareProject();
                result = command.getResult();

                if (! result.isError()) {
                    project.setTeamSettingsController(tsc);
                    Set files = tsc.getProjectFiles(true);
                    Set newFiles = new HashSet(files);
                    Set binFiles = TeamUtils.extractBinaryFilesFromSet(newFiles);
                    command = repository.commitAll(newFiles, binFiles, Collections.EMPTY_SET, files, Config.getString("team.import.initialMessage"));
                    result = command.getResult();
                }

                stopProgressBar();

                EventQueue.invokeLater(new Runnable() {
                    public void run()
                    {
                        handleServerResponse(result);
                        if(! result.isError()) {
                            setStatus(Config.getString("team.shared"));
                        }
                        else {
                            clearStatus();
                        }
                    }
                });
            }
        };
        thread.start();
    }
}
