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
package bluej.debugger.jdi;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import bluej.Boot;
import bluej.Config;
import bluej.classmgr.BPClassLoader;
import bluej.debugger.Debugger;
import bluej.debugger.DebuggerObject;
import bluej.debugger.DebuggerResult;
import bluej.debugger.DebuggerTerminal;
import bluej.debugger.ExceptionDescription;
import bluej.debugger.SourceLocation;
import bluej.debugger.gentype.GenTypeClass;
import bluej.runtime.ExecServer;
import bluej.utility.Debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassObjectReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VMMismatchException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.ThreadDeathEvent;
import com.sun.jdi.event.ThreadStartEvent;
import com.sun.jdi.event.VMStartEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;

/**
 * A class implementing the execution and debugging primitives needed by BlueJ.
 * 
 * Execution and debugging is implemented here on a second ("remote") virtual
 * machine, which gets started from here via the JDI interface.
 * 
 * @author Michael Kolling
 * @version $Id: VMReference.java 6260 2009-04-20 07:20:37Z davmac $
 * 
 * The startup process is as follows:
 * 
 * 1. Debugger spawns a MachineLoaderThread which begins to load the debug vm
 *    Any access to the debugger during this time uses getVM() which waits
 *    for the machine to be loaded.
 *    (see JdiDebugger.MachineLoaderThread).
 * 2. The MachineLoaderThread creates a VMReference representing the vm. The
 *    VMReference in turn creates a VMEventHandler to receive events from the
 *    debug VM.
 * 3. A "ClassPrepared" event is received telling BlueJ that the ExecServer
 *    class has been loaded. At this point, breakpoints are set in certain
 *    places within the server class. Execution in the debug VM continues.
 * 4. The ExecServer "main" method spawns two threads. One is the "server"
 *    thread used to run user code. The "worker" thread is used for helper
 *    functions which do not execute user code paths. Both threads hit the
 *    breakpoints which have been set. This causes a breakpoint event to occur.
 * 5. The breakpoint events are trapped. When the server thread hits the
 *    "vmStarted" breakpoint, the VM is considered to be started.
 * 
 * We can now execute commands on the remote VM by invoking methods using the
 * server thread (which is suspended at the breakpoint). 
 * 
 * Non-user code used by BlueJ is run a seperate "worker" thread.
 */
class VMReference
{
    // the class name of the execution server class running on the remote VM
    static final String SERVER_CLASSNAME = "bluej.runtime.ExecServer";

    // the field name of the static field within that class
    // the name of the method used to signal a System.exit()
    // pending for removal when exit scheme is tested.
    //    static final String SERVER_EXIT_MARKER_METHOD_NAME = "exitMarker";

    // the name of the method used to suspend the ExecServer
    static final String SERVER_STARTED_METHOD_NAME = "vmStarted";

    // the name of the method used to suspend the ExecServer
    static final String SERVER_SUSPEND_METHOD_NAME = "vmSuspend";

    // A map which can be used to map instances of VirtualMachine to VMReference 
    private static Map vmToReferenceMap = new HashMap();
    
    // ==== instance data ====

    // we have a tight coupling between us and the JdiDebugger
    // that creates us
    private JdiDebugger owner = null;

    // The remote virtual machine and process we are referring to
    private VirtualMachine machine = null;
    private Process remoteVMprocess = null;

    // The handler for virtual machine events
    private VMEventHandler eventHandler = null;

    // the class reference to ExecServer
    private ClassType serverClass = null;

    // the thread running inside the ExecServer
    private ThreadReference serverThread = null;
    private boolean serverThreadStarted = false;
    private BreakpointRequest serverBreakpoint;

    // the worker thread running inside the ExecServer
    private ThreadReference workerThread = null;
    private boolean workerThreadReady = false;
    private BreakpointRequest workerBreakpoint;

    // a record of the threads we start up for
    // redirecting ExecServer streams
    private IOHandlerThread inputStreamRedirector = null;
    private IOHandlerThread outputStreamRedirector = null;
    private IOHandlerThread errorStreamRedirector = null;

    // the current class loader in the ExecServer
    private ClassLoaderReference currentLoader = null;

    private int exitStatus;
    private ExceptionDescription lastException;
    
    // A counter for giving names to shared memory blocks for the shared
    // memory transport
    static private int shmCount = 0;
    // array index of memory transport parameter 
    private int transportIndex = 0;
    
    private boolean isDefaultEncoding = true;
    private String streamEncoding = null;

    /**
     * Launch a remote debug VM using a TCP/IP socket.
     * 
     * @param initDir
     *            the directory to have as a current directory in the remote VM
     * @param mgr
     *            the virtual machine manager
     * @return an instance of a VirtualMachine or null if there was an error
     */
    public VirtualMachine localhostSocketLaunch(File initDir, DebuggerTerminal term, VirtualMachineManager mgr)
    {
        final int CONNECT_TRIES = 5; // try to connect max of 5 times
        final int CONNECT_WAIT = 500; // wait half a sec between each connect

        int portNumber;
        String [] launchParams;

        // launch the VM using the runtime classpath.
        Boot boot = Boot.getInstance();
        File [] filesPath = BPClassLoader.toFiles(boot.getRuntimeUserClassPath());
        String allClassPath = BPClassLoader.toClasspathString(filesPath);
        
        ArrayList<String> paramList = new ArrayList<String>(10);
        paramList.add(Config.getJDKExecutablePath(null, "java"));
        
        //check if any vm args are specified in Config, at the moment these
        //are only Locale options: user.language and user.country
        
        paramList.addAll(Config.getDebugVMArgs());
        
        paramList.add("-classpath");
        paramList.add(allClassPath);
        paramList.add("-Xdebug");
        paramList.add("-Xnoagent");
        if (Config.isMacOS()) {
            paramList.add("-Xdock:icon=" + Config.getBlueJIconPath() + "/" + Config.getVMIconsName());
            paramList.add("-Xdock:name=" + Config.getVMDockName());
        }
        paramList.add("-Xrunjdwp:transport=dt_socket,server=y");
        
        // set index of memory transport, this may be used later if socket launch
        // will not work
        transportIndex = paramList.size() - 1;
        paramList.add(SERVER_CLASSNAME);
        
        // set output encoding if specified, default is to use system default
        // this gets passed to ExecServer's main as an arg which can then be 
        // used to specify encoding
        streamEncoding = Config.getPropString("bluej.terminal.encoding", null);
        isDefaultEncoding = (streamEncoding == null);
        if(!isDefaultEncoding) {
            paramList.add(streamEncoding);
        }
        
        launchParams = (String[]) paramList.toArray(new String[0]);

        String transport = Config.getPropString("bluej.vm.transport");
        
        AttachingConnector tcpipConnector = null;
        AttachingConnector shmemConnector = null;

        Throwable tcpipFailureReason = null;
        Throwable shmemFailureReason = null;
        
        // Attempt to connect via TCP/IP transport
        
        List connectors = mgr.attachingConnectors();
        AttachingConnector connector = null;

        // find the known connectors
        Iterator it = connectors.iterator();
        while (it.hasNext()) {
            AttachingConnector c = (AttachingConnector) it.next();
            
            if (c.transport().name().equals("dt_socket")) {
                tcpipConnector = c;
            }
            else if (c.transport().name().equals("dt_shmem")) {
                shmemConnector = c;
            }
        }

        connector = tcpipConnector;

        // If the transport has been explicitly set the shmem in bluej.defs,
        // try to use the dt_shmem connector first.
        if (transport.equals("dt_shmem") && shmemConnector != null)
            connector = null;

        for (int i = 0; i < CONNECT_TRIES; i++) {
            
            if (connector != null) {
                try {
                    final StringBuffer listenMessage = new StringBuffer();
                    remoteVMprocess = launchVM(initDir, launchParams, listenMessage, term);
                    
                    portNumber = extractPortNumber(listenMessage.toString());
                    
                    if (portNumber == -1) {
                        closeIO();
                        remoteVMprocess.destroy();
                        remoteVMprocess = null;
                        throw new Exception() {
                            public void printStackTrace()
                            {
                                Debug.message("Could not find port number to connect to debugger");
                                Debug.message("Line received from debugger was: " + listenMessage);
                            }
                        };
                    }
                    
                    Map arguments = connector.defaultArguments();
                    
                    Connector.Argument hostnameArg = (Connector.Argument) arguments.get("hostname");
                    Connector.Argument portArg = (Connector.Argument) arguments.get("port");
                    Connector.Argument timeoutArg = (Connector.Argument) arguments.get("timeout");
                    
                    if (hostnameArg == null || portArg == null) {
                        throw new Exception() {
                            public void printStackTrace() {
                                Debug.message("incompatible JPDA socket launch connector");
                            }
                        };
                    }
                    
                    hostnameArg.setValue("127.0.0.1");
                    portArg.setValue(Integer.toString(portNumber));
                    if (timeoutArg != null) {
                        // The timeout appears to be in milliseconds.
                        // The default is apparently no timeout.
                        timeoutArg.setValue("1000");
                    }
                    
                    VirtualMachine m = null;
                    
                    try {
                        m = connector.attach(arguments);
                    }
                    catch (Throwable t) {
                        // failed to connect.
                        closeIO();
                        remoteVMprocess.destroy();
                        remoteVMprocess = null;
                        throw t;
                    }
                    Debug.log("Connected to debug VM via dt_socket transport...");
                    machine = m;
                    setupEventHandling();
                    waitForStartup();
                    Debug.log("Communication with debug VM fully established.");
                    return m;
                }
                catch(Throwable t) {
                    tcpipFailureReason = t;
                }
            }
            
            // Attempt launch using shared memory transport, if available
            
            connector = shmemConnector;
            
            if (connector != null) {
                try {
                    Map arguments = connector.defaultArguments();
                    Connector.Argument addressArg = (Connector.Argument) arguments.get("name");
                    if (addressArg == null) {
                        throw new Exception() {
                            public void printStackTrace()
                            {
                                Debug.message("Shared memory connector is incompatible - no 'name' argument");
                            }
                        };
                    }
                    else {
                        String shmName = "bluej" + shmCount++;
                        addressArg.setValue(shmName);
                        
                        launchParams[transportIndex] = "-Xrunjdwp:transport=dt_shmem,address=" + shmName + ",server=y,suspend=y";
                        
                        StringBuffer listenMessage = new StringBuffer();
                        remoteVMprocess = launchVM(initDir, launchParams, listenMessage,term);
                        
                        VirtualMachine m = null;
                        try {
                            m = connector.attach(arguments);
                        }
                        catch (Throwable t) {
                            // failed to connect.
                            closeIO();
                            remoteVMprocess.destroy();
                            remoteVMprocess = null;
                            throw t;
                        }
                        Debug.log("Connected to debug VM via dt_shmem transport...");
                        machine = m;
                        setupEventHandling();
                        waitForStartup();
                        Debug.log("Communication with debug VM fully established.");
                        return m;
                    }
                }
                catch(Throwable t) {
                    shmemFailureReason = t;
                }
            }
            
            // Do a small wait between connection attempts
            try {
                if (i != CONNECT_TRIES - 1)
                    Thread.sleep(CONNECT_WAIT);
            }
            catch (InterruptedException ie) { break; }
            connector = tcpipConnector;
        }

        // failed to connect
        Debug.message("Failed to connect to debug VM. Reasons follow:");
        if (tcpipConnector != null && tcpipFailureReason != null) {
            Debug.message("dt_socket transport:");
            tcpipFailureReason.printStackTrace();
        }
        if (shmemConnector != null && shmemFailureReason != null) {
            Debug.message("dt_shmem transport:");
            tcpipFailureReason.printStackTrace();
        }
        if (shmemConnector == null && tcpipConnector == null) {
            Debug.message(" No suitable transports available.");
        }
        
        return null;
    }
    
    private void setupEventHandling()
    {
        // indicate the events we want to receive
        EventRequestManager erm = machine.eventRequestManager();
        erm.createExceptionRequest(null, false, true).enable();
        erm.createClassPrepareRequest().enable();
        erm.createThreadStartRequest().enable();
        erm.createThreadDeathRequest().enable();

        // start the VM event handler (will handle the VMStartEvent
        // which will set the machine running)
        eventHandler = new VMEventHandler(this, machine);
    }

    /**
     * Launch the debug VM and set up the I/O connectors to the terminal.
     * @param initDir   the directory which the vm should be started in
     * @param params    the parameters (including executable as first param)
     * @param line      a buffer which receives the first line of output from
     *                  the debug vm process
     * @param term      the terminal to connect to process I/O
     */
    private Process launchVM(File initDir, String [] params, StringBuffer line, DebuggerTerminal term)
        throws IOException
    {    
        Process vmProcess = Runtime.getRuntime().exec(params, null, initDir);
        BufferedReader br = new BufferedReader(new InputStreamReader(vmProcess.getInputStream()));
        String listenMessage = br.readLine();
        line.append(listenMessage);
        
        // grab anything else the VM spits out before we try to connect to it.
        try {
            br = new BufferedReader(new InputStreamReader(vmProcess.getErrorStream()));
            StringBuffer extra = new StringBuffer();
            // Two streams to check: standard output and standard error
                
            char [] buf = new char[1024];
            for (int i = 0; i < 5; i++) {
                Thread.sleep(200);
                
                // discontinue if no data available or stream closed
                if (! br.ready())
                    break;
                int len = br.read(buf);
                if (len == -1)
                    break;
                
                extra.append(buf, 0, len);
            }
            if (extra.length() != 0) {
                Debug.message("Extra output from debug VM on launch:" + extra);
            }
        }
        catch (InterruptedException ie) {}
        
        // redirect standard streams from process to Terminal
        // error stream System.err
        Reader errorReader = null;
        // output stream System.out
        Reader outReader = null;
        // input stream System.in
        Writer inputWriter = null;
        
        if(isDefaultEncoding) {
            errorReader = new InputStreamReader(vmProcess.getErrorStream());
            outReader = new InputStreamReader(vmProcess.getInputStream());
            inputWriter = new OutputStreamWriter(vmProcess.getOutputStream());            
        }
        // if specified in bluej.defs
        else {
            errorReader = new InputStreamReader(vmProcess.getErrorStream(), streamEncoding); 
            outReader = new InputStreamReader(vmProcess.getInputStream(), streamEncoding);
            inputWriter = new OutputStreamWriter(vmProcess.getOutputStream(), streamEncoding);
        }
        
        errorStreamRedirector = redirectIOStream(errorReader, term.getErrorWriter());
        outputStreamRedirector = redirectIOStream(outReader, term.getWriter());
        inputStreamRedirector = redirectIOStream(term.getReader(), inputWriter);
        
        return vmProcess;
    }

    /**
     * Parse the message printed when starting up in server=y mode but when no
     * port is specified. The message contains the port number that we should
     * use to connect with.
     * 
     * @param msg
     *            the message printed by the debug vm
     * @return the port number to use or -1 in case of error
     */
    private int extractPortNumber(String msg)
    {
        int colonIndex = msg.indexOf(":");
        int val = -1;

        if (! msg.startsWith("Listening for transport dt_socket at address:")) {
            return -1;
        }
        
        try {
            if (colonIndex > -1) {
                val = Integer.parseInt(msg.substring(colonIndex + 1).trim());
            }
        }
        catch (NumberFormatException nfe) {
            return -1;
        }

        return val;
    }

    /**
     * Create the second virtual machine and start the execution server (class
     * ExecServer) on that machine.
     */
    public VMReference(JdiDebugger owner, DebuggerTerminal term, File initialDirectory)
        throws JdiVmCreationException
    {
        this.owner = owner;
        
        // machine will be suspended at startup
        machine = localhostSocketLaunch(initialDirectory, term, Bootstrap.virtualMachineManager());
        //machine = null; //uncomment to simulate inabilty to create debug VM
        
        if (machine == null) {
            throw new JdiVmCreationException();
        }
        
        // Add our machine into the map
        vmToReferenceMap.put(machine, this);
    }

    /**
     * Wait for all our virtual machine initialisation to occur.
     */
    public synchronized boolean waitForStartup()
    {
        serverThreadStartWait();
        
        if (! setupServerConnection(machine))
            return false;
        
        return true;
    }

    /**
     * Close down this virtual machine.
     */
    public synchronized void close()
    {
        if (machine != null) {
            closeIO();
            // cause the debug VM to exit when disposed
            try {
                setStaticFieldValue(serverClass, ExecServer.WORKER_ACTION_NAME, machine.mirrorOf(ExecServer.EXIT_VM));
                machine.dispose();
            }
            catch(VMDisconnectedException vmde) {}
        }
    }

    /**
     * Close I/O redirectors.
     */
    public void closeIO()
    {
        // close our IO redirectors
        if (inputStreamRedirector != null) {
            inputStreamRedirector.close();
            inputStreamRedirector.interrupt();
        }

        if (errorStreamRedirector != null) {
            errorStreamRedirector.close();
            errorStreamRedirector.interrupt();
        }

        if (outputStreamRedirector != null) {
            outputStreamRedirector.close();
            outputStreamRedirector.interrupt();
        }
    }

    /**
     * This method is called by the VMEventHandler when the execution server
     * class (ExecServer) has been loaded into the VM. We use this to set a
     * breakpoint in the server class. This is really still part of the
     * initialisation process.
     */
    void serverClassPrepared()
    {
        // remove the "class prepare" event request (not needed anymore)

        EventRequestManager erm = machine.eventRequestManager();
        List list = erm.classPrepareRequests();
        erm.deleteEventRequests(list);

        try {
            serverClass = (ClassType) findClassByName(SERVER_CLASSNAME, null);
        }
        catch (ClassNotFoundException cnfe) {
            throw new IllegalStateException("can't find class " + SERVER_CLASSNAME + " in debug virtual machine");
        }

        // add the breakpoints (these may be cleared later on and so will
        // need to be readded)
        serverClassAddBreakpoints();
    }

    /**
     * This breakpoint is used to stop the server process to make it wait for
     * our task signals. (We later use the suspended process to perform our task
     * requests.)
     */
    private void serverClassAddBreakpoints()
    {
        EventRequestManager erm = machine.eventRequestManager();

        // set a breakpoint in the vm started method
        {
            Method startedMethod = findMethodByName(serverClass, SERVER_STARTED_METHOD_NAME);
            if (startedMethod == null) {
                throw new IllegalStateException("can't find method " + SERVER_CLASSNAME + "."
                        + SERVER_STARTED_METHOD_NAME);
            }
            Location loc = startedMethod.location();
            serverBreakpoint = erm.createBreakpointRequest(loc);
            serverBreakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            // the presence of this property indicates to breakEvent that we are
            // a special type of breakpoint
            serverBreakpoint.putProperty(SERVER_STARTED_METHOD_NAME, "yes");
            serverBreakpoint.putProperty(VMEventHandler.DONT_RESUME, "yes");
            serverBreakpoint.enable();
        }

        // set a breakpoint in the suspend method
        {
            Method suspendMethod = findMethodByName(serverClass, SERVER_SUSPEND_METHOD_NAME);
            if (suspendMethod == null) {
                throw new IllegalStateException("can't find method " + SERVER_CLASSNAME + "."
                        + SERVER_SUSPEND_METHOD_NAME);
            }
            Location loc = suspendMethod.location();
            workerBreakpoint = erm.createBreakpointRequest(loc);
            workerBreakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            // the presence of this property indicates to breakEvent that we are
            // a special type of breakpoint
            workerBreakpoint.putProperty(SERVER_SUSPEND_METHOD_NAME, "yes");
            // the presence of this property indicates that we should not
            // be restarted after receiving this event
            workerBreakpoint.putProperty(VMEventHandler.DONT_RESUME, "yes");
            workerBreakpoint.enable();
        }

    }

    /**
     * Find the components on the remote VM that we need to talk to it: the
     * execServer object, the performTaskMethod, and the serverThread. These
     * three variables (mirrors to the remote entities) are set up here. This
     * needs to be done only once.
     */
    private boolean setupServerConnection(VirtualMachine vm)
    {
        if (serverClass == null) {
            Debug.reportError("server class not initialised!");
            return false;
        }

        // get our main server thread
        // serverThread = (ThreadReference) getStaticFieldObject(serverClass, ExecServer.MAIN_THREAD_NAME);

        // get our worker thread
        workerThread = (ThreadReference) getStaticFieldObject(serverClass, ExecServer.WORKER_THREAD_NAME);

        if (serverThread == null || workerThread == null) {
            Debug.reportError("Cannot find fields on remote VM");
            return false;
        }

        //Debug.message(" connection to remote VM established");
        return true;
    }

    // -- all methods below here are for after the VM has started up

    /**
     * Instruct the remote machine to construct a new class loader and return its
     * reference.
     * 
     * May throw VMDisconnectedException.
     * 
     * @param urls  the classpath as an array of URL
     */
    ClassLoaderReference newClassLoader(URL [] urls)
    {
        synchronized(workerThread) {
            workerThreadReadyWait();
            setStaticFieldValue(serverClass, ExecServer.WORKER_ACTION_NAME, machine.mirrorOf(ExecServer.NEW_LOADER));
            
            StringBuffer newcpath = new StringBuffer(200);
            for (int index = 0; index < urls.length; index++) {
                newcpath.append ( urls[index].toString());
                newcpath.append ('\n');
            }
            
            setStaticFieldObject(serverClass, ExecServer.CLASSPATH_NAME, newcpath.toString());
            
            workerThreadReady = false;
            workerThread.resume();
            workerThreadReadyWait();
            
            currentLoader = (ClassLoaderReference) getStaticFieldObject(serverClass, ExecServer.WORKER_RETURN_NAME);
            
            return currentLoader;
        }
    }
    
    /**
     * Get an ObjectReference mirroring a String. May throw
     * VMDisconnectedException, VMOutOfMemoryException.
     * 
     * @param value  The string to mirror on the remote VM.
     * @return       The mirror object
     */
    public StringReference getMirror(String value)
    {
        return machine.mirrorOf(value);
    }
    
    /**
     * Load a class in the remote machine and return its reference. Note that
     * this function never returns null.
     * 
     * @return a Reference to the class mirrored in the remote VM
     * @throws ClassNotFoundException
     */
    ReferenceType loadClass(String className)
        throws ClassNotFoundException
    {
        synchronized(workerThread) {
            workerThreadReadyWait();
            setStaticFieldValue(serverClass, ExecServer.WORKER_ACTION_NAME, machine.mirrorOf(ExecServer.LOAD_CLASS));
            
            setStaticFieldObject(serverClass, ExecServer.CLASSNAME_NAME, className);
            
            workerThreadReady = false;
            workerThread.resume();
            workerThreadReadyWait();
            
            ClassObjectReference robject = (ClassObjectReference) getStaticFieldObject(serverClass, ExecServer.WORKER_RETURN_NAME);
            if (robject == null)
                throw new ClassNotFoundException(className);
            
            return robject.reflectedType();
        }
        
    }
    
    /**
     * Load a class in the remote VM using the given class loader.
     * @param className  The name of the class to load
     * @param clr        The remote classloader reference to use
     * @return     A reference to the loaded class, or null if the class could not be loaded.
     */
    ReferenceType loadClass(String className, ClassLoaderReference clr)
    {
        synchronized(workerThread) {
            workerThreadReadyWait();
            setStaticFieldValue(serverClass, ExecServer.CLASSLOADER_NAME, clr);
            
            try {
                ReferenceType rt = loadClass(className);
                return rt;
            }
            catch (Exception cnfe) {
                // ClassNotFoundException or VMDisconnectedException
                return null;
            }
        }
    }
    
    /**
     * Load and initialize a class in the remote machine, and return a reference to it.
     * Initialization causes static initializer assignments and blocks to be executed in
     * the remote machine. This method will not return until all such blocks have completed
     * execution.
     * 
     * @param className  The name of the class to load
     * @return           A reference to the class
     * @throws ClassNotFoundException  If the class could not be found
     */
    ReferenceType loadInitClass(String className)
        throws ClassNotFoundException
    {
        try {
            serverThreadStartWait();
            
            // Store the class and method to call
            setStaticFieldObject(serverClass, ExecServer.CLASS_TO_RUN_NAME, className);
            setStaticFieldValue(serverClass, ExecServer.EXEC_ACTION_NAME, machine.mirrorOf(ExecServer.LOAD_INIT_CLASS));
            
            // Resume the thread, wait for it to finish and the new thread to start
            serverThreadStarted = false;
            resumeServerThread();
            serverThreadStartWait();
            
            // Get return value
            ClassObjectReference rval = (ClassObjectReference) getStaticFieldObject(serverClass, ExecServer.METHOD_RETURN_NAME);
            if (rval == null)
                throw new ClassNotFoundException("Remote class not found: " + className);
            
            // check for and report exceptions which occurred during initialization
            ObjectReference exception = getStaticFieldObject(serverClass, ExecServer.EXCEPTION_NAME);
            if (exception != null) {
                exceptionEvent(new InvocationException(exception));
            }
            
            return rval.reflectedType();
        }
        catch (VMDisconnectedException vde) {
            throw new ClassNotFoundException("Remote class not loaded due to VM termination.");
        }
    }

    /**
     * "Start" a class (i.e. invoke its main method)
     * 
     * @param loader
     *            the class loader to use
     * @param classname
     *            the class to start
     * @param eventParam
     *            when a BlueJEvent is generated for a breakpoint, this
     *            parameter is passed as the event parameter
     */
    public DebuggerResult runShellClass(String className)
    {
        // Calls to this method are protected by serverThreadLock in JdiDebugger
        
        // Debug.message("[VMRef] starting " + className);
        // ** call Shell.run() **
        try {
            exitStatus = Debugger.NORMAL_EXIT;

            serverThreadStartWait();
            
            // Store the class and method to call
            setStaticFieldObject(serverClass, ExecServer.CLASS_TO_RUN_NAME, className);
            setStaticFieldValue(serverClass, ExecServer.EXEC_ACTION_NAME, machine.mirrorOf(ExecServer.EXEC_SHELL));
            
            // Resume the thread, wait for it to finish and the new thread to start
            serverThreadStarted = false;
            resumeServerThread();
            serverThreadStartWait();
            
            // Get return value and check for exceptions
            ObjectReference rval = getStaticFieldObject(serverClass, ExecServer.METHOD_RETURN_NAME);
            if (rval == null) {
                ObjectReference exception = getStaticFieldObject(serverClass, ExecServer.EXCEPTION_NAME);
                if (exception != null) {
                    exceptionEvent(new InvocationException(exception));
                    return new DebuggerResult(lastException);
                }
            }
            
            ClassObjectReference execdClass = (ClassObjectReference) getStaticFieldObject(serverClass, ExecServer.EXECUTED_CLASS_NAME);
            ClassType ctype = (ClassType) execdClass.reflectedType();
            Field rfield = ctype.fieldByName("__bluej_runtime_result");
            if (rfield != null) {
                rval = (ObjectReference) ctype.getValue(rfield);
                if (rval != null) {
                    GenTypeClass rgtype = (GenTypeClass) JdiReflective.fromField(rfield, ctype);
                    JdiObject robj = JdiObject.getDebuggerObject(rval, rgtype);
                    ctype.setValue(rfield, null);
                    return new DebuggerResult(robj);
                }
            }
            return new DebuggerResult((DebuggerObject) null);
        }
        catch (VMDisconnectedException e) {
            exitStatus = Debugger.TERMINATED;
            return new DebuggerResult(exitStatus);
        }
        catch (Exception e) {
            // remote invocation failed
            Debug.reportError("starting shell class failed: " + e);
            e.printStackTrace();
            exitStatus = Debugger.EXCEPTION;
            lastException = new ExceptionDescription("Internal BlueJ error: unexpected exception in remote VM\n" + e);
        }
        
        return new DebuggerResult(lastException);
    }
    
    /**
     * Invoke the default constructor for some class, and return the resulting object.
     */
    public DebuggerResult instantiateClass(String className)
    {
        ObjectReference obj = null;
        exitStatus = Debugger.NORMAL_EXIT;
        try {
            obj = invokeConstructor(className);
        }
        catch (VMDisconnectedException e) {
            exitStatus = Debugger.TERMINATED;
            // return null; // debugger state change handled elsewhere
            return new DebuggerResult(Debugger.TERMINATED);
        }
        catch (Exception e) {
            // remote invocation failed
            Debug.reportError("starting shell class failed: " + e);
            e.printStackTrace();
            exitStatus = Debugger.EXCEPTION;
            lastException = new ExceptionDescription("Internal BlueJ error: unexpected exception in remote VM\n" + e);
        }
        if (obj == null) {
            return new DebuggerResult(lastException);
        }
        else {
            return new DebuggerResult(JdiObject.getDebuggerObject(obj));
        }
    }

    /**
     * Invoke a particular constructor with arguments. The parameter types
     * of the constructor must be supplied (String[]) as well as the
     * argument values (ObjectReference []).
     * 
     * @param className  The name of the class to construct an instance of
     * @param paramTypes The parameter types of the constructor (class names)
     * @param args      The argument values to use in the constructor call
     * 
     * @return  The newly constructed object (or null if error/exception
     *          occurs)
     */
    public DebuggerResult instantiateClass(String className, String [] paramTypes, ObjectReference [] args)
    {
        ObjectReference obj = null;
        exitStatus = Debugger.NORMAL_EXIT;
        try {
            obj = invokeConstructor(className, paramTypes, args);
        }
        catch (VMDisconnectedException e) {
            exitStatus = Debugger.TERMINATED;
            return new DebuggerResult(exitStatus); // debugger state change handled elsewhere 
        }
        catch (Exception e) {
            // remote invocation failed
            Debug.reportError("starting shell class failed: " + e);
            e.printStackTrace();
            exitStatus = Debugger.EXCEPTION;
            lastException = new ExceptionDescription("Internal BlueJ error: unexpected exception in remote VM\n" + e);
        }
        if (obj == null) {
            return new DebuggerResult(lastException);
        }
        else {
            return new DebuggerResult(JdiObject.getDebuggerObject(obj));
        }
    }
    
    /**
     * Return the status of the last invocation. One of (NORMAL_EXIT,
     * FORCED_EXIT, EXCEPTION, TERMINATED).
     * 
     * (?? Question: What is the difference between "FORCED_EXIT" and
     *  "TERMINATED"? We only seem to use the latter -dm)
     */
    public int getExitStatus()
    {
        return exitStatus;
    }

    /**
     * Return the text of the last exception.
     */
    public ExceptionDescription getException()
    {
        return lastException;
    }

    /**
     * The VM has reached its startup point.
     */
    public void vmStartEvent(VMStartEvent vmse)
    {
        serverThreadStarted = false;
    }

    /**
     * The VM has been disconnected or ended.
     */
    public void vmDisconnectEvent()
    {
        synchronized (this) {
            // Do the owner disconnect first, because it is synchronized on
            // JdiDebugger. This allows machine loader thread to check the exit
            // status in a meaningful way.
            owner.vmDisconnect();
            
            // If VM disconnect occurs during invocation, the server thread won't
            // restart in this VM; the method waiting for it to start will hang
            // indefinitely unless we kick it here.
            exitStatus = Debugger.TERMINATED;
            if (!serverThreadStarted)
                notifyAll();
        }
        
        if (workerThread != null) {
            synchronized (workerThread) {
                if (!workerThreadReady)
                    workerThread.notifyAll();
            }
        }

        synchronized (vmToReferenceMap) {
            vmToReferenceMap.remove(machine);
        }
    }

    /**
     * A thread has started.
     */
    public void threadStartEvent(ThreadStartEvent tse)
    {
        owner.threadStart(tse.thread());
    }

    /**
     * A thread has died.
     */
    public void threadDeathEvent(ThreadDeathEvent tde)
    {
        ThreadReference tr = tde.thread();
        owner.threadDeath(tr);

        // There appears to be a VM bug related to system.exit() being called
        // in an invocation thread. The event is only seen as a thread death.
        // Only affects some platforms/vm versions some of the time.
        //if (tr == serverThread && serverThreadStarted || tr == workerThread)
        //    close();
    }

    /**
     * An exception has occurred in a thread.
     * 
     * It doesn't really make sense to do anything here. Any exception which occurs
     * in the primary execution thread does not come through here.
     */
    public void exceptionEvent(ExceptionEvent exc)
    {
        ObjectReference remoteException = exc.exception();

        // get the exception text
        // attention: the following depends on the (undocumented) fact that
        // the internal exception message field is named "detailMessage".
        Field msgField = remoteException.referenceType().fieldByName("detailMessage");
        StringReference msgVal = (StringReference) remoteException.getValue(msgField);

        String exceptionText = (msgVal == null ? null : msgVal.value());
        String excClass = exc.exception().type().name();

        // PENDING: to be removed after exit scheme is tested
        // if (excClass.equals("bluej.runtime.ExitException")) {
        // 
        // // this was a "System.exit()", not a real exception!
        // exitStatus = Debugger.FORCED_EXIT;
        // owner.raiseStateChangeEvent(Debugger.RUNNING, Debugger.IDLE);
        // lastException = new ExceptionDescription(exceptionText);
        // }
        // else {
        // real exception

        //Location loc = exc.location();
        //String sourceClass = loc.declaringType().name();
        //String fileName;
        //try {
        //    fileName = loc.sourceName();
        //} catch (AbsentInformationException e) {
        //    fileName = null;
        //}
        //int lineNumber = loc.lineNumber();

        List stack = JdiThread.getStack(exc.thread());
        //exitStatus = Debugger.EXCEPTION;
        //lastException = new ExceptionDescription(excClass, exceptionText, stack);
        //        }
    }

    /**
     * Invoke an arbitrary method on an object, using the worker thread.
     * If the called method exits via an exception, this method returns null.
     * 
     * @param o     The object to invoke the method on
     * @param m     The method to invoke
     * @param args  The arguments to pass to the method (List of Values)
     * @return      The return Value from the method
     */
    private Value safeInvoke(ObjectReference o, Method m, List args)
    {
        synchronized (workerThread) {
            workerThreadReadyWait();
            Value v = null;

            try {
                v = o.invokeMethod(workerThread, m, args, ObjectReference.INVOKE_SINGLE_THREADED);
            }
            catch (ClassNotLoadedException cnle) {}
            catch (InvalidTypeException ite) {}
            catch (IncompatibleThreadStateException itse) {}
            catch (InvocationException ie) {}

            return v;
        }
    }
    
    public void exceptionEvent(InvocationException exc)
    {
        List empty = new LinkedList();
        
        ObjectReference remoteException = exc.exception();
        Field msgField = remoteException.referenceType().fieldByName("detailMessage");
        StringReference msgVal = (StringReference) remoteException.getValue(msgField);
        String exceptionText = (msgVal == null ? null : msgVal.value());
        String excClass = exc.exception().type().name();
        
        ReferenceType remoteType = exc.exception().referenceType();
        List getStackTraceMethods = remoteType.methodsByName("getStackTrace");
        Method getStackTrace = (Method)getStackTraceMethods.get(0);
        ArrayReference stackValue = (ArrayReference)safeInvoke(exc.exception(),  getStackTrace, empty);
        
        ObjectReference [] stackt = (ObjectReference [])stackValue.getValues().toArray(new ObjectReference[0]);
        List stack = new LinkedList();
        
        // "stackt" is now an array of Values. Each Value represents a
        // "StackTraceElement" object.
        if (stackt.length > 0) {
            ReferenceType StackTraceElementType = (ReferenceType)stackt[0].type();
            Method getClassName = (Method)StackTraceElementType.methodsByName("getClassName").get(0);
            Method getFileName = (Method)StackTraceElementType.methodsByName("getFileName").get(0);
            Method getLineNum = (Method)StackTraceElementType.methodsByName("getLineNumber").get(0);
            Method getMethodName = (Method)StackTraceElementType.methodsByName("getMethodName").get(0);
            
            for(int i = 0; i < stackt.length; i++) {
                Value classNameV = safeInvoke(stackt[i], getClassName, empty);
                Value fileNameV = safeInvoke(stackt[i], getFileName, empty);
                Value methodNameV = safeInvoke(stackt[i], getMethodName, empty);
                Value lineNumV = safeInvoke(stackt[i], getLineNum, empty);
                
                String className = ((StringReference)classNameV).value();
                String fileName = null;
                if (fileNameV != null) {
                    fileName = ((StringReference)fileNameV).value();
                }
                String methodName = ((StringReference)methodNameV).value();
                int lineNumber = ((IntegerValue)lineNumV).value();
                stack.add(new SourceLocation(className,fileName,methodName,lineNumber));
            }
        }
        
        // stack is a list of SourceLocation (bluej.debugger.SourceLocation)
        
        exitStatus = Debugger.EXCEPTION;
        lastException = new ExceptionDescription(excClass, exceptionText, stack);
    }

    
    /**
     * A breakpoint has been hit or step completed in a thread.
     */
    public void breakpointEvent(LocatableEvent event, boolean breakpoint)
    {
        // if the breakpoint is marked as with the SERVER_STARTED property
        // then this is our own breakpoint that is used to detect when a new
        // server thread has started (which happens at startup, and when user
        // code completes execution).
        if (event.request().getProperty(SERVER_STARTED_METHOD_NAME) != null) {
            // wake up the waitForStartup() method
            synchronized (this) {
                serverThreadStarted = true;
                serverThread = event.thread();
                owner.raiseStateChangeEvent(Debugger.IDLE);
                notifyAll();
            }
        }
        // if the breakpoint is marked with the SERVER_SUSPEND property
        // then it is the worker thread returning to its breakpoint
        // after completing some work. We want to leave it suspended here until
        // it is required to do more work.
        else if (event.request().getProperty(SERVER_SUSPEND_METHOD_NAME) != null) {
            
            if (workerThread == null) {
                workerThread = event.thread();
            }
            
            synchronized (workerThread) {
                workerThreadReady = true;
                workerThread.notify();
            }
        }
        else {
            // breakpoint set by user in user code
            if (serverThread.equals(event.thread())) {
                owner.raiseStateChangeEvent(Debugger.SUSPENDED);

                Location location = event.location();
                String className = location.declaringType().name();
                String fileName;
                try {
                    fileName = location.sourceName();
                }
                catch (AbsentInformationException e) {
                    fileName = null;
                }

                // A breakpoint in the shell class or a BlueJ runtime class means that
                // the user has stepped past the end of their own code
                if (fileName != null && fileName.startsWith("__SHELL")
                        || className != null && className.startsWith("bluej.runtime.")) {
                    serverThread.resume();
                    return;
                }
            }

            // signal the breakpoint/step to the user
            owner.breakpoint(event.thread(), breakpoint);
        }
    }

    // ==== code for active debugging: setting breakpoints, stepping, etc ===

    /**
     * Find and load all classes declared in the same source file as className
     * and then find the Location object for the source at the line 'line'.
     */
    private Location loadClassesAndFindLine(String className, int line)
    {
        ReferenceType remoteClass = null;
        try {
            remoteClass = loadClass(className);
        }
        catch (ClassNotFoundException cnfe) {
            return null;
        }
        List allTypesInFile = new ArrayList();

        // find all ReferenceType's declared in this source file
        buildNestedTypes(remoteClass, allTypesInFile);

        Iterator it = allTypesInFile.iterator();
        while (it.hasNext()) {
            ReferenceType r = (ReferenceType) it.next();

            try {
                List list = r.locationsOfLine(line);
                if (list.size() > 0)
                    return (Location) list.get(0);
            }
            catch (AbsentInformationException aie) {}
        }
        return null;
    }

    /**
     * Recursively construct a list of all Types started with rootType and
     * including all its nested types.
     * 
     * @param rootType
     *            the root to start building at
     * @param l
     *            the List to add the reference types to
     */
    private void buildNestedTypes(ReferenceType rootType, List l)
    {
        try {
            synchronized(workerThread) {
                workerThreadReadyWait();
                setStaticFieldValue(serverClass, ExecServer.WORKER_ACTION_NAME, machine.mirrorOf(ExecServer.LOAD_ALL));
                
                // parameters
                setStaticFieldObject(serverClass, ExecServer.CLASSNAME_NAME, rootType.name());
                
                workerThreadReady = false;
                workerThread.resume();
                
                workerThreadReadyWait();
                ObjectReference or = getStaticFieldObject(serverClass, ExecServer.WORKER_RETURN_NAME);
                ArrayReference inners = (ArrayReference) or;
                Iterator i = inners.getValues().iterator();
                while (i.hasNext()) {
                    ClassObjectReference cor = (ClassObjectReference) i.next();
                    ReferenceType rt = cor.reflectedType();
                    if (rt.isPrepared()) {
                        l.add(rt);
                    }
                }
            }
        }
        catch (VMDisconnectedException vmde) {}
        catch (VMMismatchException vmmme) {}
    }

    /**
     * Set a breakpoint at a specified line in a class.
     * 
     * @param className
     *            The class in which to set the breakpoint.
     * @param line
     *            The line number of the breakpoint.
     * @return null if there was no problem, or an error string
     */
    String setBreakpoint(String className, int line)
    {
        Location location = loadClassesAndFindLine(className, line);
        if (location == null) {
            return Config.getString("debugger.jdiDebugger.noCodeMsg");
        }
        EventRequestManager erm = machine.eventRequestManager();
        BreakpointRequest bpreq = erm.createBreakpointRequest(location);
        bpreq.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        bpreq.putProperty(VMEventHandler.DONT_RESUME, "yes");
        bpreq.enable();

        return null;
    }

    /**
     * Clear all the breakpoints at a specified line in a class.
     * 
     * @param className
     *            The class in which to clear the breakpoints.
     * @param line
     *            The line number of the breakpoint.
     * @return null if there was no problem, or an error string
     */
    String clearBreakpoint(String className, int line)
    {
        Location location = loadClassesAndFindLine(className, line);
        if (location == null) {
            return Config.getString("debugger.jdiDebugger.noCodeMsg");
        }

        EventRequestManager erm = machine.eventRequestManager();
        boolean found = false;
        List list = erm.breakpointRequests();
        for (int i = 0; i < list.size(); i++) {
            BreakpointRequest bp = (BreakpointRequest) list.get(i);
            if (bp.location().equals(location)) {
                erm.deleteEventRequest(bp);
                found = true;
            }
        }
        // bp not found
        if (found)
            return null;
        else
            return Config.getString("debugger.jdiDebugger.noBreakpointMsg");
    }

    /**
     * Return a list of the Locations of user breakpoints in the VM.
     */
    public List getBreakpoints()
    {
        // Debug.message("[VMRef] getBreakpoints()");

        EventRequestManager erm = machine.eventRequestManager();
        List breaks = new LinkedList();

        List allBreakpoints = erm.breakpointRequests();
        Iterator it = allBreakpoints.iterator();

        while (it.hasNext()) {
            BreakpointRequest bp = (BreakpointRequest) it.next();

            if (bp.location().declaringType().classLoader() == currentLoader) {
                breaks.add(bp.location());
            }
        }

        return breaks;
    }
    
    /**
     * Remove all user breakpoints
     */
    public void clearAllBreakpoints()
    {
        EventRequestManager erm = machine.eventRequestManager();
        List breaks = new LinkedList();

        List allBreakpoints = erm.breakpointRequests();
        Iterator it = allBreakpoints.iterator();

        while (it.hasNext()) {
            BreakpointRequest bp = (BreakpointRequest) it.next();
            if (bp != serverBreakpoint && bp != workerBreakpoint) {
                breaks.add(bp);
            }
        }

        erm.deleteEventRequests(breaks);
    }
    
    /**
     * Remove all breakpoints for the given class.
     */
    public void clearBreakpointsForClass(String className)
    {
        EventRequestManager erm = machine.eventRequestManager();

        List allBreakpoints = erm.breakpointRequests();
        Iterator it = allBreakpoints.iterator();
        List toDelete = new LinkedList();

        while (it.hasNext()) {
            BreakpointRequest bp = (BreakpointRequest) it.next();

            ReferenceType bpType = bp.location().declaringType();
            if (bpType.name().equals(className)
                    && bpType.classLoader() == currentLoader) {
                toDelete.add(bp);
            }
        }
        
        erm.deleteEventRequests(toDelete);
    }

    /**
     * Restore the previosuly saved breakpoints with the new classloader.
     * 
     * @param loader
     *            The new class loader to restore the breakpoints into
     */
    public void restoreBreakpoints(List saved)
    {
        // Debug.message("[VMRef] restoreBreakpoints()");

        EventRequestManager erm = machine.eventRequestManager();

        // create the list of locations - converted to the new classloader
        // this has to be done before we suspend the machine because
        // loadClassesAndFindLine needs the machine running to work
        // see bug #526
        List newSaved = new ArrayList();

        Iterator savedIterator = saved.iterator();

        while (savedIterator.hasNext()) {
            Location oldLocation = (Location) savedIterator.next();

            Location newLocation = loadClassesAndFindLine(oldLocation.declaringType().name(), oldLocation.lineNumber());

            if (newLocation != null) {
                newSaved.add(newLocation);
            }
        }

        // to stop our server thread getting away from us, lets halt the
        // VM temporarily
        synchronized(workerThread) {
            workerThreadReadyWait();
            
            // we need to throw away all the breakpoints referring to the old
            // class loader but then we need to restore our internal breakpoints
            
            // Note, we have to be careful when deleting breakpoints. There is
            // a JDI bug which causes threads to halt indefinitely when hitting
            // a breakpoint that is being deleted. That's why we wait for the
            // worker thread to be in a stopped state before we proceed, and we
            // suspend the machine to prevent problems with the server thread.
            // Also we make sure any pending breakpoint events are processed before
            // removing the breakpoints.
            machine.suspend();
            eventHandler.waitQueueEmpty();
            erm.deleteAllBreakpoints();
            serverClassAddBreakpoints();
            
            // add all the new breakpoints we have created
            Iterator it = newSaved.iterator();
            
            while (it.hasNext()) {
                Location l = (Location) it.next();
                
                BreakpointRequest bpreq = erm.createBreakpointRequest(l);
                bpreq.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
                bpreq.putProperty(VMEventHandler.DONT_RESUME, "yes");
                bpreq.enable();
            }
            machine.resume();
        }
    }

    // -- support methods --

    /**
     * Wait for the "server" thread to start. This must be synchronized on
     * serverThreadLock (in JdiDebugger).
     */
    private void serverThreadStartWait()
    {
        synchronized(this) {
            try {
                while (!serverThreadStarted) {
                    if (exitStatus == Debugger.TERMINATED)
                        throw new VMDisconnectedException();
                    wait(); // wait for new thread to start
                }
            }
            catch (InterruptedException ie) {}
        }
    }
    
    /**
     * Resume the server thread to begin executing some function.
     * 
     * Calls to this method should be synchronized on the serverThreadLock
     * (in JdiDebugger).
     */
    private void resumeServerThread()
    {
        synchronized (eventHandler) {
            serverThread.resume();
            owner.raiseStateChangeEvent(Debugger.RUNNING);
        }
        // Note, we do the state change after the resume because the state
        // change may throw VMDisconnectedException (in which case we don't
        // want to go into the RUNNING state).
    }
    
    /**
     * Wait until the "worker" thread is ready for use. This method should
     * be called with the workerThread monitor held.
     */
    private void workerThreadReadyWait()
    {
        try {
            while (!workerThreadReady) {
                if (exitStatus == Debugger.TERMINATED)
                    throw new VMDisconnectedException();
                workerThread.wait();
            }
        }
        catch(InterruptedException ie) {}
    }
    
    /**
     * Invoke the default constructor for the given class and return a reference
     * to the generated instance.
     */
    private ObjectReference invokeConstructor(String className)
    {
        // Calls to this method are serialized via serverThreadLock in JdiDebugger
        
        serverThreadStartWait();
        
        // Store the class and method to call
        setStaticFieldObject(serverClass, ExecServer.CLASS_TO_RUN_NAME, className);
        setStaticFieldValue(serverClass, ExecServer.EXEC_ACTION_NAME, machine.mirrorOf(ExecServer.INSTANTIATE_CLASS));
        
        // Resume the thread, wait for it to finish and the new thread to start
        serverThreadStarted = false;
        resumeServerThread();
        serverThreadStartWait();
        
        // Get return value and check for exceptions
        Value rval = getStaticFieldObject(serverClass, ExecServer.METHOD_RETURN_NAME);
        if (rval == null) {
            ObjectReference exception = getStaticFieldObject(serverClass, ExecServer.EXCEPTION_NAME);
            if (exception != null) {
                exceptionEvent(new InvocationException(exception));
            }
        }
        return (ObjectReference) rval;
    }
    
    /**
     * Invoke a particular constructor with arguments. The parameter types
     * of the constructor must be supplied (String[]) as well as the
     * argument values (ObjectReference []).
     * 
     * @param className  The name of the class to construct an instance of
     * @param paramTypes The parameter types of the constructor (class names)
     * @param args      The argument values to use in the constructor call
     * 
     * @return  The newly constructed object
     */
    private ObjectReference invokeConstructor(String className, String [] paramTypes, ObjectReference [] args)
    {
        // Calls to this method are serialized via serverThreadLock in JdiDebugger
        
        serverThreadStartWait();
        boolean needsMachineResume = false;
        
        try {
            int length = paramTypes.length;
            if (args.length != length) {
                throw new IllegalArgumentException();
            }

            // Store the class, parameter types and arguments

            ArrayType objectArray = (ArrayType) loadClass("[Ljava.lang.Object;");
            ArrayType stringArray = (ArrayType) loadClass("[Ljava.lang.String;");

            // avoid problems with ObjectCollectedExceptions, see:
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4257193
            // We suspend the machine which seems to prevent GC from occurring.
            machine.suspend();
            needsMachineResume = true;
            ArrayReference argsArray = objectArray.newInstance(length);
            ArrayReference typesArray = stringArray.newInstance(length);
            
            // Fill the arrays with the correct values
            for (int i = 0; i < length; i++) {
                StringReference s = machine.mirrorOf(paramTypes[i]);
                typesArray.setValue(i, s);
                argsArray.setValue(i, args[i]);
            }
            
            setStaticFieldValue(serverClass, ExecServer.PARAMETER_TYPES_NAME, typesArray);
            setStaticFieldValue(serverClass, ExecServer.ARGUMENTS_NAME, argsArray);

            setStaticFieldObject(serverClass, ExecServer.CLASS_TO_RUN_NAME, className);
            setStaticFieldValue(serverClass, ExecServer.EXEC_ACTION_NAME, machine.mirrorOf(ExecServer.INSTANTIATE_CLASS_ARGS));
            machine.resume();
            needsMachineResume = false;
            
            // Resume the thread, wait for it to finish and the new thread to start
            serverThreadStarted = false;
            resumeServerThread();
            serverThreadStartWait();
            
            // Get return value and check for exceptions
            Value rval = getStaticFieldObject(serverClass, ExecServer.METHOD_RETURN_NAME);
            if (rval == null) {
                ObjectReference exception = getStaticFieldObject(serverClass, ExecServer.EXCEPTION_NAME);
                if (exception != null) {
                    exceptionEvent(new InvocationException(exception));
                }
            }
            return (ObjectReference) rval;

        }
        catch (ClassNotFoundException cnfe) { }
        catch (ClassNotLoadedException cnle) { }
        catch (InvalidTypeException ite) { }
        finally {
            if (needsMachineResume) {
                machine.resume();
            }
        }
        
        return null;
    }
    
    // Calls to this method are serialized via serverThreadLock in JdiDebugger
    public Value invokeTestSetup(String cl)
            throws InvocationException
    {
        // Make sure the server thread has started
        serverThreadStartWait();
        
        // Store the class and method to call
        setStaticFieldObject(serverClass, ExecServer.CLASS_TO_RUN_NAME, cl);
        setStaticFieldValue(serverClass, ExecServer.EXEC_ACTION_NAME, machine.mirrorOf(ExecServer.TEST_SETUP));
        
        // Resume the thread, wait for it to finish and the new thread to start
        serverThreadStarted = false;
        resumeServerThread();
        serverThreadStartWait();
        
        // Get return value and check for exceptions
        Value rval = getStaticFieldObject(serverClass, ExecServer.METHOD_RETURN_NAME);
        if (rval == null) {
            ObjectReference e = getStaticFieldObject(serverClass, ExecServer.EXCEPTION_NAME);
            if (e != null) {
                exceptionEvent(new InvocationException(e));
                throw new InvocationException(e);
            }
        }
        return rval;
    }
    
    /**
     * Run a unit test method (including setup/teardown).
     * @param cl     The class containing the method
     * @param method The test method to run
     * @return  null if the test passed, or an ArrayReference if it fails, with:
     *          [0] = failure type ("failure"/"error")
     *          [1] = the exception message
     *          [2] = the stack trace
     *          [3] = the class of the failure point
     *          [4] = the source file name containing the failure point
     *          [5] = the line number of the failure point
     * @throws InvocationException
     */
    public Value invokeRunTest(String cl, String method)
        throws InvocationException
    {
        // Calls to this method are serialized via serverThreadLock in JdiDebugger

        serverThreadStartWait();
        
        // Store the class and method to call
        setStaticFieldObject(serverClass, ExecServer.CLASS_TO_RUN_NAME, cl);
        setStaticFieldObject(serverClass, ExecServer.METHOD_TO_RUN_NAME, method);
        setStaticFieldValue(serverClass, ExecServer.EXEC_ACTION_NAME, machine.mirrorOf(ExecServer.TEST_RUN));
        
        // Resume the thread, wait for it to finish and the new thread to start
        serverThreadStarted = false;
        resumeServerThread();
        serverThreadStartWait();
        
        Value rval = getStaticFieldObject(serverClass, ExecServer.METHOD_RETURN_NAME);
        if (rval == null) {
            ObjectReference e = getStaticFieldObject(serverClass, ExecServer.EXCEPTION_NAME);
            if (e != null) {
                exceptionEvent(new InvocationException(e));
                throw new InvocationException(e);
            }
        }
        return rval;
    }

    /**
     * Dispose of all gui windows opened from the debug vm.
     */
    void disposeWindows()
    {
        // Calls to this method are serialized via serverThreadLock in JdiDebugger

        serverThreadStartWait();
            
        // set the action to "dispose windows"
        setStaticFieldValue(serverClass, ExecServer.EXEC_ACTION_NAME, machine.mirrorOf(ExecServer.DISPOSE_WINDOWS));
        
        // Resume the thread, it then proceeds to remove open windows
        serverThreadStarted = false;
        resumeServerThread();
        // We don't bother waiting for it to finish
    }
    
    /**
     * Add an object to the object map on the debug vm.
     * @param instanceName  the name of the object to add
     * @param object        a reference to the object to add
     */
    void addObject(String scopeId, String instanceName, ObjectReference object)
    {
        try {
            synchronized(workerThread) {
                workerThreadReadyWait();
                setStaticFieldValue(serverClass, ExecServer.WORKER_ACTION_NAME, machine.mirrorOf(ExecServer.ADD_OBJECT));
                
                // parameters
                setStaticFieldObject(serverClass, ExecServer.OBJECTNAME_NAME, instanceName);
                setStaticFieldValue(serverClass, ExecServer.OBJECT_NAME, object);
                setStaticFieldObject(serverClass, ExecServer.SCOPE_ID_NAME, scopeId);
                
                workerThreadReady = false;
                workerThread.resume();
            }
        }
        catch (VMDisconnectedException vmde) {}
        catch (VMMismatchException vmmme) {}
    }
    
    /**
     * Remove an object from the object map on the debug vm.
     * @param instanceName   the name of the object to remove
     */
    synchronized void removeObject(String scopeId, String instanceName)
    {
        synchronized(workerThread) {
            try {
                workerThreadReadyWait();
                setStaticFieldValue(serverClass, ExecServer.WORKER_ACTION_NAME, machine.mirrorOf(ExecServer.REMOVE_OBJECT));
        
                // parameters
                setStaticFieldObject(serverClass, ExecServer.OBJECTNAME_NAME, instanceName);
                setStaticFieldObject(serverClass, ExecServer.SCOPE_ID_NAME, scopeId);
        
                workerThreadReady = false;
                workerThread.resume();
            }
            catch(VMDisconnectedException vmde) { }
        }
    }

    /**
     * Check whether a thread is sitting on the server thread breakpoint. 
     */
    static boolean isAtMainBreakpoint(ThreadReference tr)
    {
        try {
            return (tr.isAtBreakpoint() && SERVER_CLASSNAME.equals(tr.frame(0).location().declaringType().name()));
        }
        catch (IncompatibleThreadStateException e) {
            return false;
        }
    }

    /**
     * Get a reference to an object from a static field in some class in the
     * debug VM.
     * 
     * VMDisconnected exception may be thrown.
     * 
     * @param cl         The class containing the field
     * @param fieldName  The name of the field
     * @return    An ObjectReference referring to the object
     */
    ObjectReference getStaticFieldObject(ClassType cl, String fieldName)
    {
        Field resultField = cl.fieldByName(fieldName);

        if (resultField == null)
            throw new IllegalArgumentException("getting field " + fieldName + " resulted in no fields");

        return (ObjectReference) cl.getValue(resultField);
    }
    
    /**
     * Set the value of a static field in the debug VM.
     * @param cl         The class containing the field
     * @param fieldName  The name of the field
     * @param value      The value to which the field must be set
     */
    void setStaticFieldValue(ClassType cl, String fieldName, Value value)
    {
        Field field = cl.fieldByName(fieldName);
        
        try {
            cl.setValue(field,value);
        }
        catch(InvalidTypeException ite) { }
        catch(ClassNotLoadedException cnle) { }
    }

    /**
     * Set the value of some static field as a string. A mirror of the given
     * string value is created on the debug VM.
     */
    void setStaticFieldObject(ClassType cl, String fieldName, String value)
    {
        // Any mirror object which is created is prone to being garbage
        // collected before we can assign it to a field. This causes an
        // ObjectCollectedException. using the "disableCollection" method seems
        // to help but there is still a window between object creation and that
        // method being called, so we catch the exception and use a more
        // forceful approach in that case.
        
        try {
            StringReference s = machine.mirrorOf(value);
            s.disableCollection();
            setStaticFieldValue(cl, fieldName, s);
            s.enableCollection();
        }
        catch(ObjectCollectedException oce) {
            machine.suspend();
            StringReference s = machine.mirrorOf(value);
            setStaticFieldValue(cl, fieldName, s);
            machine.resume();
        }
    }

    /**
     * Find the mirror of a class/interface/array in the remote VM.
     * 
     * The class is expected to exist. We expect only one single class to exist
     * with this name. Throws a ClassNotFoundException if the class could not be
     * found.
     * 
     * This should only be used for classes that we know exist and are loaded ie
     * ExecServer etc.
     */
    private ReferenceType findClassByName(String className, ClassLoaderReference clr)
        throws ClassNotFoundException
    {
        // find the class
        List list = machine.classesByName(className);
        if (list.size() == 1) {
            return (ReferenceType) list.get(0);
        }
        else if (list.size() > 1) {
            Iterator iter = list.iterator();
            while (iter.hasNext()) {
                ReferenceType cl = (ReferenceType) iter.next();
                if (cl.classLoader() == clr)
                    return cl;
            }
        }
        throw new ClassNotFoundException(className);
    }

    /**
     * Find the mirror of a class/interface/array in the remote VM.
     * 
     * @param className
     *            the name of the class to find
     * @return a reference to the class
     * 
     * @throws ClassNotFoundException
     */
    public ReferenceType findClassByName(String className)
        throws ClassNotFoundException
    {
        return findClassByName(className, currentLoader);
    }

    /**
     * Find the mirror of a method in the remote VM.
     * 
     * The method is expected to exist. We expect only one single method to
     * exist with this name and report an error if more than one is found.
     */
    Method findMethodByName(ClassType type, String methodName)
    {
        List list = type.methodsByName(methodName);
        if (list.size() != 1) {
            throw new IllegalArgumentException("getting method " + methodName + " resulted in " + list.size()
                    + " methods");
        }
        return (Method) list.get(0);
    }

    /**
     * Create a thread that will retrieve any output from the remote machine and
     * direct it to our terminal (or vice versa).
     */
    private IOHandlerThread redirectIOStream(final Reader reader, final Writer writer)
    {
        IOHandlerThread thr;

        thr = new IOHandlerThread(reader, writer);
        thr.setPriority(Thread.MAX_PRIORITY - 1);
        thr.start();

        return thr;
    }

    /**
     * The thread for retrieving output from the remote machine and redirecting
     * it to the terminal.
     */
    private class IOHandlerThread extends Thread
    {
        private Reader reader;
        private Writer writer;
        private volatile boolean keepRunning = true;

        IOHandlerThread(Reader reader, Writer writer)
        {
            super("BlueJ I/O Handler");
            this.reader = reader;
            this.writer = writer;
            setPriority(Thread.MIN_PRIORITY);
        }

        public void close()
        {
            keepRunning = false;
        }

        public void run()
        {
            try {
                // An arbitrary buffer size.
                char [] chbuf = new char[4096];
                
                while (keepRunning) {
                    int numchars = reader.read(chbuf);
                    if (numchars == -1) {
                        keepRunning = false;
                    }
                    else if (keepRunning) {
                        writer.write(chbuf, 0, numchars);
                        if (! reader.ready()) {
                            writer.flush();
                        }
                    }
                }
            }
            catch (IOException ex) {
                // Debug.reportError("Cannot read output user VM.");
            }
        }
    }

    /**
     * Find the VMReference which corresponds to the supplied VirtualMachine instance.
     */
    public static VMReference getVmForMachine(VirtualMachine mc)
    {
        synchronized (vmToReferenceMap) {
            return (VMReference) vmToReferenceMap.get(mc);
        }
    }
    
    /*
    public void dumpConnectorArgs(Map arguments)
    {
        // debug code to print out all existing arguments and their
        // description
        Collection c = arguments.values();
        Iterator i = c.iterator();
        while (i.hasNext()) {
            Connector.Argument a = (Connector.Argument) i.next();
            Debug.message("arg name: " + a.name());
            Debug.message("  descr: " + a.description());
            Debug.message("  value: " + a.value());
        }
    }
    */
}
