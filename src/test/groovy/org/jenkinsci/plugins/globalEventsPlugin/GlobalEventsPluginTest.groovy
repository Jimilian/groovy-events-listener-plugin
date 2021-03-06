package org.jenkinsci.plugins.globalEventsPlugin

import org.junit.Before
import org.junit.Test

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
/**
 * Created by nickgrealy@gmail.com.
 */
class GlobalEventsPluginTest {

    GlobalEventsPlugin.DescriptorImpl plugin
    LoggerTrap logger

    @Before
    void setup(){
        // disable load method, create new plugin...
        GlobalEventsPlugin.DescriptorImpl.metaClass.load = {}
        plugin = new GlobalEventsPlugin.DescriptorImpl(ClassLoader.getSystemClassLoader())
        logger = new LoggerTrap(GlobalEventsPluginTest.name)
    }

    @Test
    void testPassingInputs(){
        plugin.safeExecGroovyCode(logger, plugin.getScriptReadyToBeExecuted("""
            assert aaa == 111
            [success:true]
            """), [aaa:111])
        assert plugin.context == [success:true]
    }

    @Test
    void testCounter(){
        int expectedValue = 1000
        plugin.putToContext("total", 0)
        plugin.setOnEventGroovyCode("context.total += 1")
        for(int i=0; i<expectedValue; i++) {
            plugin.safeExecOnEventGroovyCode(logger, [:])
            assert plugin.context == [total: i+1]
        }
        assert plugin.context == [total:expectedValue]
    }

    @Test
    void testConcurrentCounter(){
        int expectedValue = 1000
        plugin.putToContext("total", 0)
        plugin.setDisableSynchronization(false)
        plugin.setOnEventGroovyCode("context.total += 1;")

        ExecutorService executors = Executors.newFixedThreadPool(5);
        FutureTask task1 = new FutureTask(callCodeMultipleTimes(expectedValue/5 as int));
        FutureTask task2 = new FutureTask(callCodeMultipleTimes(expectedValue/5 as int));
        FutureTask task3 = new FutureTask(callCodeMultipleTimes(expectedValue/5 as int));
        FutureTask task4 = new FutureTask(callCodeMultipleTimes(expectedValue/5 as int));
        FutureTask task5 = new FutureTask(callCodeMultipleTimes(expectedValue/5 as int));
        executors.execute(task1);
        executors.execute(task2);
        executors.execute(task3);
        executors.execute(task4);
        executors.execute(task5);

        while (true) {
            if (task1.isDone() && task2.isDone() && task3.isDone() && task4.isDone() && task5.isDone() ) {
                break;
            }

            Thread.sleep(1000);
        }

        assert plugin.context == [total:expectedValue]
    }

    @Test
    void testDisableSynchronizationCounter(){
        int expectedValue = 10000
        plugin.putToContext("total", 0)
        plugin.setDisableSynchronization(true)
        plugin.setOnEventGroovyCode("context.total += 1;")
        Callable<Integer> callable = callCodeMultipleTimes(expectedValue/2 as int)

        ExecutorService executors = Executors.newFixedThreadPool(2);
        FutureTask task1 = new FutureTask(callable);
        FutureTask task2 = new FutureTask(callable);
        executors.execute(task1);
        executors.execute(task2);

        while (true) {
            if (task1.isDone() && task2.isDone()) {
                break;
            }

            Thread.sleep(1000);
        }

        assert plugin.context != [total:expectedValue]
    }

    private Callable<Integer> callCodeMultipleTimes(int number) {
        return new Callable() {
            @Override
            Integer call() throws Exception {
                for(int i=0; i<number; i++) {
                    plugin.safeExecOnEventGroovyCode(logger, [:])
                }
                return 0;
            };
        };
    }
}
