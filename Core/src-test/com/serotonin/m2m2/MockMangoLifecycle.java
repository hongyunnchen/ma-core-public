/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 *
 */
package com.serotonin.m2m2;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import com.infiniteautomation.mango.io.serial.SerialPortManager;
import com.infiniteautomation.mango.io.serial.virtual.VirtualSerialPortConfig;
import com.infiniteautomation.mango.io.serial.virtual.VirtualSerialPortConfigResolver;
import com.infiniteautomation.mango.spring.MangoRuntimeContextConfiguration;
import com.infiniteautomation.mango.util.CommonObjectMapper;
import com.serotonin.ShouldNeverHappenException;
import com.serotonin.m2m2.module.EventManagerListenerDefinition;
import com.serotonin.m2m2.module.Module;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.module.SystemSettingsListenerDefinition;
import com.serotonin.m2m2.rt.EventManager;
import com.serotonin.m2m2.rt.RuntimeManager;
import com.serotonin.m2m2.rt.event.type.AuditEventType;
import com.serotonin.m2m2.rt.event.type.EventType;
import com.serotonin.m2m2.rt.event.type.EventTypeResolver;
import com.serotonin.m2m2.rt.event.type.SystemEventType;
import com.serotonin.m2m2.rt.maint.BackgroundProcessing;
import com.serotonin.m2m2.util.MapWrap;
import com.serotonin.m2m2.util.MapWrapConverter;
import com.serotonin.m2m2.view.chart.BaseChartRenderer;
import com.serotonin.m2m2.view.chart.ChartRenderer;
import com.serotonin.m2m2.view.text.BaseTextRenderer;
import com.serotonin.m2m2.view.text.TextRenderer;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.mailingList.EmailRecipient;
import com.serotonin.m2m2.vo.mailingList.EmailRecipientResolver;
import com.serotonin.provider.Providers;
import com.serotonin.provider.TimerProvider;
import com.serotonin.timer.AbstractTimer;
import com.serotonin.util.properties.MangoProperties;

/**
 * Dummy implementation for Mango Lifecycle for use in testing,
 *   override as necessary.
 *
 * @author Terry Packer
 */
public class MockMangoLifecycle implements IMangoLifecycle{

    protected boolean enableWebConsole;
    protected int webPort;
    protected List<Module> modules;
    private final List<Runnable> STARTUP_TASKS = new ArrayList<>();
    private final List<Runnable> SHUTDOWN_TASKS = new ArrayList<>();

    //Members to use for non defaults
    protected TimerProvider<AbstractTimer> timer;
    protected MangoProperties properties;
    protected EventManager eventManager;
    protected H2InMemoryDatabaseProxy db;
    protected RuntimeManager runtimeManager;
    protected SerialPortManager serialPortManager;
    protected MockBackgroundProcessing backgroundProcessing;
    
    
    /**
     * Create a default lifecycle with an H2 web console on port 9001
     *   to view the in-memory database.
     */
    public MockMangoLifecycle(List<Module> modules) {
        this(modules, true, 9001);
    }

    public MockMangoLifecycle(List<Module> modules, boolean enableWebConsole, int webPort) {
        this.enableWebConsole = enableWebConsole;
        this.webPort = webPort;
        this.modules = modules;
    }
    
    
    /**
     * Startup a dummy Mango with a basic infrastructure
     */
    public void initialize() {

        Common.MA_HOME =  System.getProperty("ma.home");
        if(Common.MA_HOME == null)
            Common.MA_HOME = ".";

        //Add in modules
        for(Module module : modules)
            ModuleRegistry.addModule(module);

        Providers.add(IMangoLifecycle.class, this);

        //TODO Licensing Providers.add(ICoreLicense.class, new CoreLicenseDefinition());
        //TODO Licensing Providers.add(ITimedLicenseRegistrar.class, new TimedLicenseRegistrar());
        Common.free = false;

        //Startup a simulation timer provider
        Providers.add(TimerProvider.class, getSimulationTimerProvider());

        //Make sure that Common and other classes are properly loaded
        Common.envProps = getEnvProps();

        Common.JSON_CONTEXT.addResolver(new EventTypeResolver(), EventType.class);
        Common.JSON_CONTEXT.addResolver(new BaseChartRenderer.Resolver(), ChartRenderer.class);
        Common.JSON_CONTEXT.addResolver(new BaseTextRenderer.Resolver(), TextRenderer.class);
        Common.JSON_CONTEXT.addResolver(new EmailRecipientResolver(), EmailRecipient.class);
        Common.JSON_CONTEXT.addResolver(new VirtualSerialPortConfigResolver(), VirtualSerialPortConfig.class);
        Common.JSON_CONTEXT.addConverter(new MapWrapConverter(), MapWrap.class);

        for (Module module : ModuleRegistry.getModules()) {
            module.preInitialize(true, false);
        }

        //TODO This must be done only once because we have a static
        // final referece to the PointValueDao in the PointValueCache class
        // and so if you try to restart the database it doesn't get the new connection
        // for each new test.
        //Start the Database so we can use Daos (Base Dao requires this)
        if(Common.databaseProxy == null) {
            Common.databaseProxy = getDatabaseProxy();
        }
        
        if(Common.databaseProxy != null)
            Common.databaseProxy.initialize(null);
        
        //Setup the Spring Context
        springRuntimeContextInitialize();

        //Ensure we start with the proper timer
        Common.backgroundProcessing = getBackgroundProcessing();
        Common.backgroundProcessing.initialize(false);

        for (Module module : ModuleRegistry.getModules()) {
            module.postDatabase(true, false);
        }

        //Utilities
        //So we can add users etc.
        EventType.initialize();
        SystemEventType.initialize();
        AuditEventType.initialize();

        //Setup Common Object Mapper
        Common.objectMapper = new CommonObjectMapper();

        //Do this last as Event Types have listeners
        for (SystemSettingsListenerDefinition def : ModuleRegistry.getSystemSettingListenerDefinitions())
            def.registerListener();

        //Event Manager
        Common.eventManager = getEventManager();
        Common.eventManager.initialize(false);
        for (EventManagerListenerDefinition def : ModuleRegistry.getDefinitions(EventManagerListenerDefinition.class))
            Common.eventManager.addListener(def);

        Common.runtimeManager = getRuntimeManager();
        Common.runtimeManager.initialize(false);

        if(Common.serialPortManager == null)
            Common.serialPortManager = getSerialPortManager();


        for (Module module : ModuleRegistry.getModules()) {
            module.postInitialize(true, false);
        }

        for (Runnable task : STARTUP_TASKS){
            try{
                task.run();
            }catch(Exception e){
                fail(e.getMessage());
            }
        }

    }
    
    protected void springRuntimeContextInitialize() {
        AnnotationConfigWebApplicationContext runtimeContext = new AnnotationConfigWebApplicationContext();
        runtimeContext.register(MangoRuntimeContextConfiguration.class);
        runtimeContext.setId(Common.RUNTIME_CONTEXT_ID);
        runtimeContext.refresh();
        runtimeContext.start();
        Common.setRuntimeContext(runtimeContext);
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#isTerminated()
     */
    @Override
    public boolean isTerminated() {
        return false;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#terminate()
     */
    @Override
    public void terminate() {
        H2InMemoryDatabaseProxy proxy = (H2InMemoryDatabaseProxy) Common.databaseProxy;
        try {
            proxy.clean();
        } catch (Exception e) {
            throw new ShouldNeverHappenException(e);
        }
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#addStartupTask(java.lang.Runnable)
     */
    @Override
    public void addStartupTask(Runnable task) {
        STARTUP_TASKS.add(task);
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#addShutdownTask(java.lang.Runnable)
     */
    @Override
    public void addShutdownTask(Runnable task) {
        SHUTDOWN_TASKS.add(task);
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#getLifecycleState()
     */
    @Override
    public int getLifecycleState() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#getStartupProgress()
     */
    @Override
    public float getStartupProgress() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#getShutdownProgress()
     */
    @Override
    public float getShutdownProgress() {
        // TODO Auto-generated method stub
        return 0;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#loadLic()
     */
    @Override
    public void loadLic() {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#dataPointLimit()
     */
    @Override
    public Integer dataPointLimit() {
        return Integer.MAX_VALUE;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#scheduleShutdown(long, boolean, com.serotonin.m2m2.vo.User)
     */
    @Override
    public Thread scheduleShutdown(long timeout, boolean b, User user) {

        return null;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.IMangoLifecycle#isRestarting()
     */
    @Override
    public boolean isRestarting() {
        return false;
    }

    @Override
    public void reloadSslContext() {
    }

    protected TimerProvider<AbstractTimer>  getSimulationTimerProvider() {
        if(this.timer == null)
            return new SimulationTimerProvider();
        else 
            return this.timer;
    }

    protected MangoProperties getEnvProps() {
        if(this.properties == null)
            return new MockMangoProperties();
        else
            return this.properties;
    }

    protected EventManager getEventManager() {
        if(this.eventManager == null)
            return new MockEventManager();
        else
            return this.eventManager;
    }

    protected H2InMemoryDatabaseProxy getDatabaseProxy() {
        if(this.db == null)
            return new H2InMemoryDatabaseProxy(enableWebConsole, webPort);
        else 
            return this.db;
    }

    protected RuntimeManager getRuntimeManager() {
        if(this.runtimeManager == null)
            return new MockRuntimeManager();
        else
            return this.runtimeManager;
    }

    protected SerialPortManager getSerialPortManager() {
        if(this.serialPortManager == null)
            return new MockSerialPortManager();
        else
            return this.serialPortManager;
    }

    protected BackgroundProcessing getBackgroundProcessing() {
        if(this.backgroundProcessing == null)
            return new MockBackgroundProcessing(Providers.get(TimerProvider.class).getTimer());
        else
            return this.backgroundProcessing;
    }

    public boolean isEnableWebConsole() {
        return enableWebConsole;
    }

    public void setEnableWebConsole(boolean enableWebConsole) {
        this.enableWebConsole = enableWebConsole;
    }

    public void setWebPort(int webPort) {
        this.webPort = webPort;
    }

    public void setTimer(TimerProvider<AbstractTimer> timer) {
        this.timer = timer;
    }

    public void setProperties(MangoProperties properties) {
        this.properties = properties;
    }

    public void setEventManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    public void setDb(H2InMemoryDatabaseProxy db) {
        this.db = db;
    }

    public void setRuntimeManager(RuntimeManager runtimeManager) {
        this.runtimeManager = runtimeManager;
    }

    public void setSerialPortManager(SerialPortManager serialPortManager) {
        this.serialPortManager = serialPortManager;
    }

    public void setBackgroundProcessing(MockBackgroundProcessing backgroundProcessing) {
        this.backgroundProcessing = backgroundProcessing;
    }
    
    
}
