package workflows;

import org.apache.hadoop.fs.Path;
import org.apache.oozie.client.OozieClient;
import org.apache.oozie.client.WorkflowJob;
import org.apache.oozie.local.LocalOozie;
import org.apache.oozie.service.XLogService;
import org.apache.oozie.test.MiniOozieTestCase;

import java.io.*;
import java.util.Properties;
import org.apache.hadoop.fs.FileSystem;

public class CopyDealerOlgaDataTest extends MiniOozieTestCase{

  @Override
  protected void setUp() throws Exception {
    System.setProperty("oozie.test.metastore.server", "false");
    System.setProperty(XLogService.LOG4J_FILE, "oozie-log4j.properties");
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }


  public void testWorkflowRun() throws Exception {
    String wfApp = "<workflow-app xmlns='uri:oozie:workflow:0.1' name='test-wf'>" + "    <start to='end'/>"
      + "    <end name='end'/>" + "</workflow-app>";

    FileSystem fs = getFileSystem();
    Path appPath = new Path(getFsTestCaseDir(), "app");
    fs.mkdirs(appPath);
    fs.mkdirs(new Path(appPath, "lib"));

    Writer writer = new OutputStreamWriter(fs.create(new Path(appPath, "workflow.xml")));
    writer.write(wfApp);
    writer.close();

    final OozieClient wc = LocalOozie.getClient();

    Properties conf = wc.createConfiguration();
    conf.setProperty(OozieClient.APP_PATH, new Path(appPath, "workflow.xml").toString());
    conf.setProperty(OozieClient.USER_NAME, getTestUser());


    final String jobId = wc.submit(conf);
    assertNotNull(jobId);

    WorkflowJob wf = wc.getJobInfo(jobId);
    assertNotNull(wf);
    assertEquals(WorkflowJob.Status.PREP, wf.getStatus());

    wc.start(jobId);

    waitFor(1000, new Predicate() {
      public boolean evaluate() throws Exception {
        WorkflowJob wf = wc.getJobInfo(jobId);
        return wf.getStatus() == WorkflowJob.Status.SUCCEEDED;
      }
    });

    wf = wc.getJobInfo(jobId);
    assertNotNull(wf);
    assertEquals(WorkflowJob.Status.SUCCEEDED, wf.getStatus());

  }

  /**
   * Return a classpath resource as a stream.
   * <p/>
   *
   * @param path classpath for the resource.
   * @param maxLen max content length allowed.
   * @return the stream for the resource.
   * @throws IOException thrown if the resource could not be read.
   */
  public InputStream getResourceAsStream(String path, int maxLen) throws IOException {
    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    if (is == null) {
      throw new IllegalArgumentException("resource " + path + " not found");
    }
    return is;
  }

  /**
   * Return a classpath resource as a reader.
   * <p/>
   * It is assumed that the resource is a text resource.
   *
   * @param path classpath for the resource.
   * @param maxLen max content length allowed.
   * @return the reader for the resource.
   * @throws IOException thrown if the resource could not be read.
   */
  public Reader getResourceAsReader(String path, int maxLen) throws IOException {
    return new InputStreamReader(getResourceAsStream(path, maxLen));
  }

  /**
   * Copies an char input stream into an char output stream.
   *
   * @param reader reader to copy from.
   * @param writer writer to copy to.
   * @throws IOException thrown if the copy failed.
   */
  public void copyCharStream(Reader reader, Writer writer) throws IOException {
    char[] buffer = new char[4096];
    int read;
    while ((read = reader.read(buffer)) > -1) {
      writer.write(buffer, 0, read);
    }
  }

}
