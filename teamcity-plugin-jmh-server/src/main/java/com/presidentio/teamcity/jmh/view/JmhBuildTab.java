package com.presidentio.teamcity.jmh.view;

import com.intellij.openapi.diagnostic.Logger;
import com.presidentio.teamcity.jmh.entity.Benchmark;
import com.presidentio.teamcity.jmh.entity.PrimaryMetric;
import com.presidentio.teamcity.jmh.runner.common.JmhRunnerConst;
import com.presidentio.teamcity.jmh.runner.common.UnitConverter;
import com.presidentio.teamcity.jmh.runner.server.JmhRunnerBundle;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.SimpleCustomTab;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.jetbrains.annotations.NotNull;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Created by Vitaliy on 09.04.2015.
 */
public class JmhBuildTab extends SimpleCustomTab {

    private static final Logger LOGGER = Loggers.SERVER;

    public static final String BUILD_ID = "buildId";

    private SBuildServer buildServer;
    private ObjectMapper objectMapper = new ObjectMapper();

    public JmhBuildTab(@NotNull SBuildServer buildServer,
                       @NotNull PagePlaces pagePlaces,
                       @NotNull PluginDescriptor descriptor) {
        super(pagePlaces, PlaceId.BUILD_RESULTS_TAB, JmhRunnerBundle.TAB_ID,
                descriptor.getPluginResourcesPath("tabJmhView.jsp"), JmhRunnerBundle.TAB_TITLE);
        register();
        this.buildServer = buildServer;
    }

    @Override
    public boolean isAvailable(@NotNull HttpServletRequest request) {
        long buildId = Long.valueOf(request.getParameter(BUILD_ID));
        SBuild build = buildServer.findBuildInstanceById(buildId);
        return build.isFinished() && getBenchmarks(build) != null;
    }

    @Override
    public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {
        long buildId = Long.valueOf(request.getParameter(BUILD_ID));
        SBuild build = buildServer.findBuildInstanceById(buildId);
        List<SFinishedBuild> buildsBefore = buildServer.getHistory().getEntriesBefore(build, true);
        GroupedBenchmarks curGroupedBenchmark = getBenchmarks(build);
        GroupedBenchmarks prevGroupedBenchmark = new GroupedBenchmarks();
        for (SFinishedBuild sFinishedBuild : buildsBefore) {
            GroupedBenchmarks groupedBenchmarks = getBenchmarks(sFinishedBuild);
            if (groupedBenchmarks != null) {
                for (String benchmarkGroupKey : curGroupedBenchmark.keySet()) {
                    Group curGroup = curGroupedBenchmark.get(benchmarkGroupKey);
                    Group prevGroup = prevGroupedBenchmark.get(benchmarkGroupKey);
                    Group group = groupedBenchmarks.get(benchmarkGroupKey);
                    if (group != null) {
                        if (prevGroup == null) {
                            prevGroup = new Group();
                            prevGroupedBenchmark.put(benchmarkGroupKey, prevGroup);
                        }
                        for (String benchmarkKey : curGroup.keySet()) {
                            if (!prevGroup.containsKey(benchmarkKey)) {
                                Benchmark curBenchmark = curGroup.get(benchmarkKey);
                                Benchmark benchmark = group.get(benchmarkKey);
                                if (benchmark != null) {
                                    if (curBenchmark.getMode().equals(benchmark.getMode())) {
                                        prevGroup.put(benchmarkKey, 
                                                changeScoreUnit(benchmark, curBenchmark.getPrimaryMetric().getScoreUnit()));
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            }
        }
        model.put("benchmarks", curGroupedBenchmark);
        model.put("prevBenchmarks", prevGroupedBenchmark);
    }

    private Benchmark changeScoreUnit(Benchmark benchmark, String scoreUnit) {
        Benchmark result = new Benchmark(benchmark);
        PrimaryMetric primaryMetric = result.getPrimaryMetric();
        String unitFrom = primaryMetric.getScoreUnit();
        primaryMetric.setScore(UnitConverter.convert(primaryMetric.getScore(), unitFrom, scoreUnit));
        primaryMetric.setScoreError(UnitConverter.convert(primaryMetric.getScoreError(), unitFrom, scoreUnit));
        for (int i = 0; i < primaryMetric.getRawData().length; i++) {
            for (int j = 0; j < primaryMetric.getRawData()[i].length; j++) {
                primaryMetric.getRawData()[i][j] = UnitConverter.convert(primaryMetric.getRawData()[i][j], unitFrom, scoreUnit);
            }
        }
        for (int i = 0; i < primaryMetric.getScoreConfidence().length; i++) {
            primaryMetric.getScoreConfidence()[i] = UnitConverter.convert(primaryMetric.getScoreConfidence()[i], unitFrom, scoreUnit);
        }
        for (String key : primaryMetric.getScorePercentiles().keySet()) {
            primaryMetric.getScorePercentiles().put(key, UnitConverter.convert(primaryMetric.getScorePercentiles().get(key), unitFrom, scoreUnit));
        }
        return result;
    }

    private GroupedBenchmarks getBenchmarks(SBuild build) {
        File benchmarksFile = new File(build.getArtifactsDirectory(), JmhRunnerConst.OUTPUT_FILE);
        try {
            LOGGER.info("benchmarksFile=" + IOUtils.toString(new FileInputStream(benchmarksFile)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (benchmarksFile.exists()) {
            try {
                List<Benchmark> benchmarks = objectMapper.readValue(benchmarksFile,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Benchmark.class));
                return new GroupedBenchmarks(benchmarks);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
