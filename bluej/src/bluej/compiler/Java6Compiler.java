/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2010  Michael Kolling and John Rosenberg 

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
package bluej.compiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * Compiler class implemented using the JavaCompiler
 * 
 * @author Marion Zalk
 */
public class Java6Compiler extends Compiler
{
    public Java6Compiler()
    {
        setDebug(true);
    }
    
    /**
     * Compile some source files by using the JavaCompiler API. Allows for the addition of user
     * options
     * 
     * @param sources
     *            The files to compile
     * @param observer
     *            The compilation observer
     * @param internal
     *            True if compiling BlueJ-generated code (shell files) False if
     *            compiling user code
     * @return    success
     */
    public boolean compile(File[] sources, CompileObserver observer,
            boolean internal) 
    {
        boolean result = true;
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        List<String> optionsList = new ArrayList<String>();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        try
        {  
            //setup the filemanager
            StandardJavaFileManager sjfm = jc.getStandardFileManager(diagnostics, null, null);
            List <File>pathList = new ArrayList<File>();
            List<File> outputList= new ArrayList<File>();
            outputList.add(getDestDir());
            pathList.addAll(Arrays.asList(getProjectClassLoader().getClassPathAsFiles()));
            sjfm.setLocation(StandardLocation.SOURCE_PATH, pathList);
            sjfm.setLocation(StandardLocation.CLASS_PATH, pathList);
            sjfm.setLocation(StandardLocation.CLASS_OUTPUT, outputList);
            //get the source files for compilation  
            Iterable<? extends JavaFileObject> compilationUnits1 =
                sjfm.getJavaFileObjectsFromFiles(Arrays.asList(sources));
            //add any options
            if(isDebug())
                optionsList.add("-g");
            if(isDeprecation())
                optionsList.add("-deprecation"); 
            addUserSpecifiedOptions(optionsList, COMPILER_OPTIONS);
            //compile
            jc.getTask(null, sjfm, diagnostics, optionsList, null, compilationUnits1).call();
            sjfm.close();            

        }
        catch(IOException e)
        {
            e.printStackTrace(System.out);
            return false;
        }

        //Query diagnostics for error/warning messages
        List<Diagnostic<? extends JavaFileObject>> diagnosticList = diagnostics.getDiagnostics();        
        String src=null;
        int pos=0;
        String msg=null;
        boolean error=false;
        boolean warning=false;
        int diagnosticErrorPosition=-1;
        int diagnosticWarningPosition=-1;
        //ensure an error is printed if there is one, else use the warning/s; note/s
        //(errors should have priority in the diagnostic list, but this is just in case not)
        for (int i=0; i< diagnosticList.size(); i++){
            if (diagnosticList.get(i).getKind().equals(Diagnostic.Kind.ERROR))
            {
                diagnosticErrorPosition=i;
                error=true;
                warning=false;
                break;
            }
            if (diagnosticList.get(i).getKind().equals(Diagnostic.Kind.WARNING)||
                    diagnosticList.get(i).getKind().equals(Diagnostic.Kind.NOTE))
            {
                warning=true;
                //just to ensure the first instance of the warning position is recorded 
                //(not the last position)
                if (diagnosticWarningPosition==-1){
                    diagnosticWarningPosition=i;
                }
            }
        }
        //diagnosticErrorPosition can either be the warning/error
        if (diagnosticErrorPosition<0)
            diagnosticErrorPosition=diagnosticWarningPosition;
        //set the necessary values
        if (warning||error){
            if (((Diagnostic<?>)diagnosticList.get(diagnosticErrorPosition)).getSource()!=null)
                src= ((Diagnostic<?>)diagnosticList.get(diagnosticErrorPosition)).getSource().toString();
            pos= (int)((Diagnostic<?>)diagnosticList.get(diagnosticErrorPosition)).getLineNumber();
            
            // Handle compiler error messages 
            if (error) 
            {
                result=false;
                msg=((Diagnostic<?>)diagnosticList.get(diagnosticErrorPosition)).getMessage(null);
                msg=processMessage(src, pos, msg);
                observer.errorMessage(src, pos, msg);
            }
            // Handle compiler warning messages  
            // If it is a warning message, need to get all the messages
            if (warning) 
            {
                for (int i=diagnosticErrorPosition; i< diagnosticList.size(); i++){
                    //'display unchecked warning messages' in the preferences dialog is unchecked
                    //therefore notes should not be displayed
                    //warnings can still be displayed
                    if (internal && ((Diagnostic<?>)diagnosticList.get(i)).getKind().equals(Diagnostic.Kind.NOTE)){
                        continue;
                    }
                    else
                    {
                        msg=((Diagnostic<?>)diagnosticList.get(i)).getMessage(null);
                        observer.warningMessage(src, pos, msg);
                    }
                }              
            }
        }
        return result;
    }

    /**
     * @param  String msg representing the message retrieved from the diagnostic tool
     * processMessage tidies up the message returned from the diagnostic tool
     * @return message String
     */
    protected String processMessage(String src, int pos, String msg)
    {
        // The message is in this format: 
        //   path and filename:line number:message
        // i.e includes the path and line number; so we need to strip that off
        String expected = src + ":" + pos + ": ";
        if (! msg.startsWith(expected)) {
            // Hmm, it's not a format we recgonize
            return src;
        }
        
        String message = msg.substring(expected.length());
        if (message.contains("cannot resolve symbol")
                || message.contains("cannot find symbol")
                || message.contains("incompatible types")) {
            // divide the message into lines so we can retrieve necessary values
            int index1, index2;
            String line2, line3;
            index1=message.indexOf('\n');
            if (index1 == -1) {
                // We don't know how to handle this.
                return msg;
            }
            index2=message.indexOf('\n',index1+1);
            //i.e there are only 2 lines not 3
            if (index2 < index1) {
                line2 = message.substring(index1).trim();
                line3 = "";
            }
            else {
                line2 = message.substring(index1, index2).trim();
                line3 = message.substring(index2).trim();
            }
            message=message.substring(0, index1);

            //e.g incompatible types
            //found   : int
            //required: java.lang.String
            if (line2.startsWith("found") && line2.indexOf(':') != -1) {
                message= message +" - found "+line2.substring(line2.indexOf(':')+2, line2.length());
            }
            if (line3.startsWith("required") && line3.indexOf(':') != -1) {
                message= message +" but expected "+line3.substring(line3.indexOf(':')+2, line3.length());
            }
            //e.g cannot find symbol
            //symbol: class Persons
            if (line2.startsWith("symbol") && line2.indexOf(':') != -1) {
                message= message +" - "+line2.substring(line2.indexOf(':')+2, line2.length());
            }
        }
        return message;
    }
}