package org.mapfish.print.processor.map;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.Multimap;
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
import org.mapfish.print.URIUtils;
import org.mapfish.print.config.Configuration;
import org.mapfish.print.config.ConfigurationFactory;
import org.mapfish.print.config.Template;
import org.mapfish.print.output.Values;
import org.mapfish.print.test.util.ImageSimilarity;
import org.mapfish.print.wrapper.json.PJsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

/** Tests map rotation for WMTS and GeoJSON layer. */
public class CreateMapProcessorFixedScaleAndCenterWMTSRotationTest
    extends AbstractMapfishSpringTest {
  public static final String BASE_DIR = "center_wmts_rotation_fixedscale";

  @Autowired private ConfigurationFactory configurationFactory;
  @Autowired private TestHttpClientFactory requestFactory;
  @Autowired private ForkJoinPool forkJoinPool;

  public static PJsonObject loadJsonRequestData() throws IOException {
    return parseJSONObjectFromFile(
        CreateMapProcessorFixedScaleAndCenterWMTSRotationTest.class,
        BASE_DIR + "/requestData.json");
  }

  @Test
  @DirtiesContext
  public void testExecute() throws Exception {
    requestFactory.registerHandler(
        input -> {
          final String host = BASE_DIR + ".com";
          return (("" + input.getHost()).contains(host)) || input.getAuthority().contains(host);
        },
        createFileHandler(
            uri -> {
              final Multimap<String, String> parameters = URIUtils.getParameters(uri);
              String column = parameters.get("TILECOL").iterator().next();
              String row = parameters.get("TILEROW").iterator().next();
              return "/map-data/ny-tiles/" + column + "x" + row + ".png";
            }));
    requestFactory.registerHandler(
        input -> {
          final String host = "center_wmts_rotation_fixedscale.json";
          return (("" + input.getHost()).contains(host)) || input.getAuthority().contains(host);
        },
        createFileHandler(uri -> "/map-data" + uri.getPath()));

    final Configuration config = configurationFactory.getConfig(getFile(BASE_DIR + "/config.yaml"));
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

    new ImageSimilarity(getFile(BASE_DIR + "/expectedSimpleImage.png"))
        .assertSimilarity(layerGraphics, 630, 294, 0);
  }
}
