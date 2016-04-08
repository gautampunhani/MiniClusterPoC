package workflows;

import com.github.sakserv.minicluster.config.ConfigVars;
import com.github.sakserv.minicluster.impl.HdfsLocalCluster;
import com.github.sakserv.minicluster.impl.MRLocalCluster;
import com.github.sakserv.minicluster.impl.OozieLocalServer;
import com.github.sakserv.minicluster.oozie.util.OozieShareLibUtil;
import com.github.sakserv.propertyparser.PropertyParser;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.oozie.client.OozieClient;
import org.apache.oozie.client.WorkflowJob;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MapReduceWorkflowTest {

    // Logger
    private static final Logger LOG = LoggerFactory.getLogger(MapReduceWorkflowTest.class);

    // Setup the property parser
    private static PropertyParser propertyParser;
    private static OozieLocalServer oozieLocalServer;
    private static HdfsLocalCluster hdfsLocalCluster;
    private static MRLocalCluster mrLocalCluster;

    static {
        try {
            propertyParser = new PropertyParser(ConfigVars.DEFAULT_PROPS_FILE);
            propertyParser.parsePropsFile();
        } catch (IOException e) {
            LOG.error("Unable to load property file: {}", propertyParser.getProperty(ConfigVars.DEFAULT_PROPS_FILE));
        }
    }

    @BeforeClass
    public static void setUp() throws Exception {

        hdfsLocalCluster = getHDFSLocalCluster();
        hdfsLocalCluster.start();

        mrLocalCluster = getMRLocalCluster();
        mrLocalCluster.start();

        oozieLocalServer = getOozieLocalServer();
        oozieLocalServer.start();
    }

    private static OozieLocalServer getOozieLocalServer() {
        return new OozieLocalServer.Builder()
                .setOozieTestDir(propertyParser.getProperty(ConfigVars.OOZIE_TEST_DIR_KEY))
                .setOozieHomeDir(propertyParser.getProperty(ConfigVars.OOZIE_HOME_DIR_KEY))
                .setOozieUsername(System.getProperty("user.name"))
                .setOozieGroupname(propertyParser.getProperty(ConfigVars.OOZIE_GROUPNAME_KEY))
                .setOozieYarnResourceManagerAddress(propertyParser.getProperty(
                        ConfigVars.YARN_RESOURCE_MANAGER_ADDRESS_KEY))
                .setOozieHdfsDefaultFs(hdfsLocalCluster.getHdfsConfig().get("fs.defaultFS"))
                .setOozieConf(hdfsLocalCluster.getHdfsConfig())
                .setOozieHdfsShareLibDir(propertyParser.getProperty(ConfigVars.OOZIE_HDFS_SHARE_LIB_DIR_KEY))
                .setOozieShareLibCreate(Boolean.parseBoolean(
                        propertyParser.getProperty(ConfigVars.OOZIE_SHARE_LIB_CREATE_KEY)))
                .setOozieLocalShareLibCacheDir(propertyParser.getProperty(
                        ConfigVars.OOZIE_LOCAL_SHARE_LIB_CACHE_DIR_KEY))
                .setOoziePurgeLocalShareLibCache(Boolean.parseBoolean(propertyParser.getProperty(
                        ConfigVars.OOZIE_PURGE_LOCAL_SHARE_LIB_CACHE_KEY)))
                .build();
    }

    private static MRLocalCluster getMRLocalCluster() {
        return new MRLocalCluster.Builder()
                .setNumNodeManagers(Integer.parseInt(propertyParser.getProperty(ConfigVars.YARN_NUM_NODE_MANAGERS_KEY)))
                .setJobHistoryAddress(propertyParser.getProperty(ConfigVars.MR_JOB_HISTORY_ADDRESS_KEY))
                .setResourceManagerAddress(propertyParser.getProperty(ConfigVars.YARN_RESOURCE_MANAGER_ADDRESS_KEY))
                .setResourceManagerHostname(propertyParser.getProperty(ConfigVars.YARN_RESOURCE_MANAGER_HOSTNAME_KEY))
                .setResourceManagerSchedulerAddress(propertyParser.getProperty(
                        ConfigVars.YARN_RESOURCE_MANAGER_SCHEDULER_ADDRESS_KEY))
                .setResourceManagerResourceTrackerAddress(propertyParser.getProperty(
                        ConfigVars.YARN_RESOURCE_MANAGER_RESOURCE_TRACKER_ADDRESS_KEY))
                .setResourceManagerWebappAddress(propertyParser.getProperty(
                        ConfigVars.YARN_RESOURCE_MANAGER_WEBAPP_ADDRESS_KEY))
                .setUseInJvmContainerExecutor(Boolean.parseBoolean(propertyParser.getProperty(
                        ConfigVars.YARN_USE_IN_JVM_CONTAINER_EXECUTOR_KEY)))
                .setHdfsDefaultFs(hdfsLocalCluster.getHdfsConfig().get("fs.defaultFS"))
                .setConfig(hdfsLocalCluster.getHdfsConfig())
                .build();
    }

    private static HdfsLocalCluster getHDFSLocalCluster() {
        return new HdfsLocalCluster.Builder()
                .setHdfsNamenodePort(Integer.parseInt(propertyParser.getProperty(ConfigVars.HDFS_NAMENODE_PORT_KEY)))
                .setHdfsTempDir(propertyParser.getProperty(ConfigVars.HDFS_TEMP_DIR_KEY))
                .setHdfsNumDatanodes(Integer.parseInt(propertyParser.getProperty(ConfigVars.HDFS_NUM_DATANODES_KEY)))
                .setHdfsEnablePermissions(
                        Boolean.parseBoolean(propertyParser.getProperty(ConfigVars.HDFS_ENABLE_PERMISSIONS_KEY)))
                .setHdfsFormat(Boolean.parseBoolean(propertyParser.getProperty(ConfigVars.HDFS_FORMAT_KEY)))
                .setHdfsEnableRunningUserAsProxyUser(Boolean.parseBoolean(
                        propertyParser.getProperty(ConfigVars.HDFS_ENABLE_RUNNING_USER_AS_PROXY_USER)))
                .setHdfsConfig(new Configuration())
                .build();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        oozieLocalServer.stop();
        mrLocalCluster.stop();
        hdfsLocalCluster.stop();
    }

    @Test
    public void testSubmitWorkflow() throws Exception {

        LOG.info("OOZIE: Test Submit Workflow Start");

        FileSystem hdfsFs = hdfsLocalCluster.getHdfsFileSystemHandle();
        OozieClient oozie = oozieLocalServer.getOozieClient();

        Path appPath = new Path(hdfsFs.getHomeDirectory(), "testApp");
        hdfsFs.mkdirs(new Path(appPath, "lib"));
        Path workflow = new Path(appPath, "workflow.xml");

        //write workflow.xml
        String wfApp = "<workflow-app xmlns='uri:oozie:workflow:0.1' name='test-wf'>" +
                "    <start to='end'/>" +
                "    <end name='end'/>" +
                "</workflow-app>";

        Writer writer = new OutputStreamWriter(hdfsFs.create(workflow));
        writer.write(wfApp);
        writer.close();

        //write job.properties
        Properties conf = oozie.createConfiguration();
        conf.setProperty(OozieClient.APP_PATH, workflow.toString());
        conf.setProperty(OozieClient.USER_NAME, UserGroupInformation.getCurrentUser().getUserName());

        //submit and check
        final String jobId = oozie.submit(conf);
        WorkflowJob wf = oozie.getJobInfo(jobId);
        assertNotNull(wf);
        assertEquals(WorkflowJob.Status.PREP, wf.getStatus());

        LOG.info("OOZIE: Workflow: {}", wf.toString());
        hdfsFs.close();

    }

    @Test
    public void testSubmitWorkflowFromFile() throws Exception {

        LOG.info("OOZIE: Test Submit Workflow Start");

        FileSystem hdfsFs = hdfsLocalCluster.getHdfsFileSystemHandle();
        OozieClient oozie = oozieLocalServer.getOozieClient();

        Path appPath = new Path(hdfsFs.getHomeDirectory(), "testApp");
        hdfsFs.mkdirs(new Path(appPath, "lib"));
        Path workflow = new Path(appPath, "workflow.xml");

        //write workflow.xml

        Reader reader = getResourceAsReader("workflow-fs.xml", -1);
        Writer writer = new OutputStreamWriter(hdfsFs.create(workflow));
        copyCharStream(reader, writer);
        writer.close();

        //write job.properties
        Properties conf = oozie.createConfiguration();
        conf.setProperty(OozieClient.APP_PATH, workflow.toString());
        conf.setProperty(OozieClient.USER_NAME, UserGroupInformation.getCurrentUser().getUserName());

        //submit and check
        final String jobId = oozie.submit(conf);
        WorkflowJob wf = oozie.getJobInfo(jobId);
        assertNotNull(wf);
        assertEquals(WorkflowJob.Status.PREP, wf.getStatus());

        oozie.start(jobId);

        while (oozie.getJobInfo(jobId).getStatus() == WorkflowJob.Status.RUNNING) {
            System.out.println("Workflow job running ...");
            Thread.sleep(10 * 1000);
        }

        wf = oozie.getJobInfo(jobId);

        System.out.print("ERROR MSG: " + wf.getActions().get(1).getErrorMessage());

        assertNotNull(wf);
        assertEquals(WorkflowJob.Status.SUCCEEDED, wf.getStatus());

        LOG.info("OOZIE: Workflow: {}", wf.toString());
        hdfsFs.close();

    }

    @Test
    public void testOozieShareLib() throws Exception {

        LOG.info("OOZIE: Test Oozie Share Lib Start");

        FileSystem hdfsFs = hdfsLocalCluster.getHdfsFileSystemHandle();
        OozieShareLibUtil oozieShareLibUtil = new OozieShareLibUtil(oozieLocalServer.getOozieHdfsShareLibDir(),
                oozieLocalServer.getOozieShareLibCreate(), oozieLocalServer.getOozieLocalShareLibCacheDir(),
                oozieLocalServer.getOoziePurgeLocalShareLibCache(), hdfsFs);
        oozieShareLibUtil.createShareLib();

        // Validate the share lib dir was created and contains a single directory
        FileStatus[] fileStatuses = hdfsFs.listStatus(new Path(oozieLocalServer.getOozieHdfsShareLibDir()));
        assertEquals(1, fileStatuses.length);
        hdfsFs.close();

    }

    public Reader getResourceAsReader(String path, int maxLen) throws IOException {
        return new InputStreamReader(getResourceAsStream(path, maxLen));
    }

    public void copyCharStream(Reader reader, Writer writer) throws IOException {
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) > -1) {
            writer.write(buffer, 0, read);
        }
    }

    public InputStream getResourceAsStream(String path, int maxLen) throws IOException {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new IllegalArgumentException("resource " + path + " not found");
        }
        return is;
    }

}
