/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package umss.cobluej.server.svn;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author paolocastro
 */
public class RepositoryManagerTest {

    public RepositoryManagerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of create method, of class RepositoryManager.
     */
    @Test
    public void testCreate() {
        System.out.println("create");
        RepositoryManager instance = new RepositoryManager("http://192.168.1.102/svn");
        instance.create();
        assertNotNull(instance.getRepository());
    }
}