package org.mapfish.print.processor.map;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.mapfish.print.AbstractMapfishSpringTest;
import org.mapfish.print.TestHttpClientFactory;
import org.mapfish.print.config.Configuration;
import org.mapfish.print.config.ConfigurationFactory;
import org.mapfish.print.config.Template;
import org.mapfish.print.output.Values;
import org.mapfish.print.test.util.ImageSimilarity;
import org.mapfish.print.wrapper.json.PJsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Basic test of the Map processor.
 *
 * <p>Created by Jesse on 3/26/14.
 */
public class CreateMapProcessorFixedScaleCenterOsmTest extends AbstractMapfishSpringTest {
  public static final String BASE_DIR = "center_osm_fixedscale/";

  @Autowired private ConfigurationFactory configurationFactory;
  @Autowired private TestHttpClientFactory requestFactory;
  @Autowired private ForkJoinPool forkJoinPool;

  private static PJsonObject loadJsonRequestData() throws IOException {
    return parseJSONObjectFromFile(
        CreateMapProcessorFixedScaleCenterOsmTest.class, BASE_DIR + "requestData.json");
  }

  @Test
  @DirtiesContext
  public void testExecute() throws Exception {
    final String host = "center_osm_fixedscale";
    requestFactory.registerHandler(
        input ->
            (("" + input.getHost()).contains(host + ".osm"))
                || input.getAuthority().contains(host + ".osm"),
        createFileHandler(uri -> "/map-data/osm" + uri.getPath()));
    requestFactory.registerHandler(
        input ->
            (("" + input.getHost()).contains(host + ".json"))
                || input.getAuthority().contains(host + ".json"),
        createFileHandler(uri -> "/map-data" + uri.getPath()));
    final Configuration config = configurationFactory.getConfig(getFile(BASE_DIR + "config.yaml"));
    final Template template = config.getTemplate("main");
    PJsonObject requestData = loadJsonRequestData();
    Values values =
        new Values(
            new HashMap<String, String>(),
            requestData,
            template,
            getTaskDirectory(),
            this.requestFactory,
            new File("."),
            HTTP_REQUEST_MAX_NUMBER_FETCH_RETRY,
            HTTP_REQUEST_FETCH_RETRY_INTERVAL_MILLIS,
            new AtomicBoolean(false));

    final ForkJoinTask<Values> taskFuture =
        this.forkJoinPool.submit(template.getProcessorGraph().createTask(values));
    taskFuture.get();

    @SuppressWarnings("unchecked")
    List<URI> layerGraphics = (List<URI>) values.getObject("layerGraphics", List.class);
    assertEquals(2, layerGraphics.size());

    new ImageSimilarity(getFile(BASE_DIR + "expectedSimpleImage.png"))
        .assertSimilarity(layerGraphics, 780, 330, 0);
  }
}
