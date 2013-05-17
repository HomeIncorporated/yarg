/**
 *
 * @author degtyarjov
 * @version $Id$
 */
package com.haulmont.newreport.reporting;

import com.haulmont.newreport.formatters.Formatter;
import com.haulmont.newreport.formatters.factory.FormatterFactoryInput;
import com.haulmont.newreport.formatters.factory.FormatterFactory;
import com.haulmont.newreport.loaders.DataLoader;
import com.haulmont.newreport.loaders.factory.LoaderFactory;
import com.haulmont.newreport.structure.BandDefinition;
import com.haulmont.newreport.structure.DataSet;
import com.haulmont.newreport.structure.Report;
import com.haulmont.newreport.structure.ReportTemplate;
import com.haulmont.newreport.structure.impl.Band;
import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.*;

public class Reporting implements ReportingAPI {
    protected FormatterFactory formatterFactory;

    protected LoaderFactory loaderFactory;

    public void setFormatterFactory(FormatterFactory formatterFactory) {
        this.formatterFactory = formatterFactory;
    }

    public void setLoaderFactory(LoaderFactory loaderFactory) {
        this.loaderFactory = loaderFactory;
    }

    @Override
    public void runReport(RunParams runParams, OutputStream outputStream) {
        runReport(runParams.report, runParams.templateCode, runParams.params, outputStream);
    }

    @Override
    public byte[] runReport(RunParams runParams) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        runReport(runParams.report, runParams.templateCode, runParams.params, result);
        return result.toByteArray();
    }

    private void runReport(Report report, String templateCode, Map<String, Object> params, OutputStream outputStream) {
        ReportTemplate reportTemplate = report.getReportTemplates().get(templateCode);
        String extension = StringUtils.substringAfterLast(reportTemplate.getDocumentName(), ".");
        Band rootBand = new Band(Band.ROOT_BAND_NAME);
        rootBand.setData(params);
        FormatterFactoryInput factoryInput = new FormatterFactoryInput(extension, rootBand, reportTemplate, outputStream);
        Formatter formatter = formatterFactory.createFormatter(factoryInput);

        rootBand.setBandDefinitionNames(new HashSet<String>());
        for (BandDefinition definition : report.getRootBandDefinition().getChildrenBandDefinitions()) {
            List<Band> bands = createBands(definition, rootBand, params);
            rootBand.addChildren(bands);
            rootBand.getBandDefinitionNames().add(definition.getName());//todo not only first level bands
        }

        formatter.renderDocument();
    }

    private List<Band> createBands(BandDefinition definition, Band parentBand, Map<String, Object> params) {
        List<Map<String, Object>> outputData = getBandData(definition, parentBand, params);
        return createBandsList(definition, parentBand, outputData, params);
    }

    private List<Band> createBandsList(BandDefinition definition, Band parentBand, List<Map<String, Object>> outputData, Map<String, Object> params) {
        List<Band> bandsList = new ArrayList<Band>();
        for (Map<String, Object> data : outputData) {
            Band band = new Band(definition.getName(), parentBand, definition.getOrientation());
            band.setData(data);
            Collection<BandDefinition> childrenBandDefinitions = definition.getChildrenBandDefinitions();
            for (BandDefinition childDefinition : childrenBandDefinitions) {
                List<Band> childBands = createBands(childDefinition, band, params);
                band.addChildren(childBands);
            }
            bandsList.add(band);
        }
        return bandsList;
    }

    private List<Map<String, Object>> getBandData(BandDefinition definition, Band parentBand, Map<String, Object> params) {
        Collection<DataSet> dataSets = definition.getDataSets();
        //add input params to band
        if (dataSets == null || dataSets.size() == 0)
            return Collections.singletonList(params);

        Iterator<DataSet> dataSetIterator = dataSets.iterator();
        DataSet firstDataSet = dataSetIterator.next();

        List<Map<String, Object>> result;

        //gets data from first dataset
        result = getDataSetData(parentBand, firstDataSet, params);

        //adds data from second and following datasets to result
        while (dataSetIterator.hasNext()) {//todo reimplement
            List<Map<String, Object>> dataSetData = getDataSetData(parentBand, dataSetIterator.next(), params);
            for (int j = 0; (j < result.size()) && (j < dataSetData.size()); j++) {
                result.get(j).putAll(dataSetData.get(j));
            }
        }

        if (result != null)
            //add output params to band
            for (Map<String, Object> map : result) {
                map.putAll(params);
            }

        return result;
    }

    private List<Map<String, Object>> getDataSetData(Band parentBand, DataSet dataSet, Map<String, Object> paramsMap) {
        DataLoader dataLoader = loaderFactory.createDataLoader(dataSet.getLoaderType());
        return dataLoader.loadData(dataSet, parentBand, paramsMap);
    }
}