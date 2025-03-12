/*
    Mango - Open Source M2M - http://mango.serotoninsoftware.com
    Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
    @author Matthew Lohbihler
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.mango.view.chart;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import com.serotonin.json.JsonRemoteEntity;
import com.serotonin.json.JsonRemoteProperty;
import com.serotonin.mango.DataTypes;
import com.serotonin.mango.rt.dataImage.PointValueFacade;
import com.serotonin.mango.rt.dataImage.PointValueTime;
import com.serotonin.mango.view.ImplDefinition;
import com.serotonin.mango.view.stats.AnalogStatistics;
import com.serotonin.mango.view.stats.StartsAndRuntimeList;
import com.serotonin.mango.view.stats.ValueChangeCounter;
import com.serotonin.mango.vo.DataPointVO;

@JsonRemoteEntity
public class StatisticsChartRenderer extends TimePeriodChartRenderer {
    private static ImplDefinition definition = new ImplDefinition("chartRendererStats", "STATS",
            "chartRenderer.statistics", new int[] { DataTypes.ALPHANUMERIC, DataTypes.BINARY, DataTypes.MULTISTATE,
                    DataTypes.NUMERIC });

    public static ImplDefinition getDefinition() {
        return definition;
    }

    public String getTypeName() {
        return definition.getName();
    }

    public ImplDefinition getDef() {
        return definition;
    }

    @JsonRemoteProperty
    private boolean includeSum;

    public StatisticsChartRenderer() {
        // no op
    }

    public StatisticsChartRenderer(int timePeriod, int numberOfPeriods, boolean includeSum) {
        super(timePeriod, numberOfPeriods);
        this.includeSum = includeSum;
    }

    public boolean isIncludeSum() {
        return includeSum;
    }

    public void setIncludeSum(boolean includeSum) {
        this.includeSum = includeSum;
    }

    // Refactored method to reduce feature envy by delegating specific logic to helper classes.
    public void addDataToModel(Map<String, Object> model, DataPointVO point) {
        long startTime = getStartTime();
        PointValueFacade pointValueFacade = new PointValueFacade(point.getId());
        List<PointValueTime> values = pointValueFacade.getPointValues(startTime);

        int dataTypeId = point.getPointLocator().getDataTypeId();

        // The start value is the value of the point at the start of the period for this renderer.
        PointValueTime startValue = getStartValue(pointValueFacade, startTime, values);

        // Now delegate statistics generation to separate classes
        if (startValue != null || values.size() > 0) {
            if (dataTypeId == DataTypes.BINARY || dataTypeId == DataTypes.MULTISTATE) {
                generateRuntimeStats(model, startValue, values, startTime);
            }
            else if (dataTypeId == DataTypes.NUMERIC) {
                generateNumericStats(model, startValue, values, startTime);
            }
            else if (dataTypeId == DataTypes.ALPHANUMERIC) {
                generateAlphanumericStats(model, startValue, values);
            }
        }

        model.put("logEntries", values.size());
    }

    // Helper method to get the start value
    private PointValueTime getStartValue(PointValueFacade pointValueFacade, long startTime, List<PointValueTime> values) {
        PointValueTime startValue = null;
        if (values.size() == 0 || values.get(0).getTime() > startTime) {
            PointValueTime valueTime = pointValueFacade.getPointValueBefore(startTime);
            if (valueTime != null)
                startValue = new PointValueTime(valueTime.getValue(), startTime);
        }
        return startValue;
    }

    // Refactored method to handle runtime statistics
    private void generateRuntimeStats(Map<String, Object> model, PointValueTime startValue, List<PointValueTime> values, long startTime) {
        StartsAndRuntimeList stats = new StartsAndRuntimeList(startValue, values, startTime, startTime + getDuration());
        model.put("start", stats.getRealStart());
        model.put("end", stats.getEnd());
        model.put("startsAndRuntimes", stats.getData());
    }

    // Refactored method to handle numeric statistics
    private void generateNumericStats(Map<String, Object> model, PointValueTime startValue, List<PointValueTime> values, long startTime) {
        AnalogStatistics stats = new AnalogStatistics(startValue, values, startTime, startTime + getDuration());
        model.put("start", stats.getRealStart());
        model.put("end", stats.getEnd());
        model.put("minimum", stats.getMinimum());
        model.put("minTime", stats.getMinTime());
        model.put("maximum", stats.getMaximum());
        model.put("maxTime", stats.getMaxTime());
        model.put("average", stats.getAverage());
        if (includeSum)
            model.put("sum", stats.getSum());
        model.put("count", stats.getCount());
        model.put("noData", stats.isNoData());
    }

    // Refactored method to handle alphanumeric statistics
    private void generateAlphanumericStats(Map<String, Object> model, PointValueTime startValue, List<PointValueTime> values) {
        ValueChangeCounter stats = new ValueChangeCounter(startValue, values);
        model.put("changeCount", stats.getChangeCount());
    }

    //
    // / 
    // / Serialization
    // / 
    //
    private static final long serialVersionUID = -1;
    private static final int version = 2;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(version);
        out.writeBoolean(includeSum);
    }

    private void readObject(ObjectInputStream in) throws IOException {
        int ver = in.readInt();

        // Switch on the version of the class so that version changes can be elegantly handled.
        if (ver == 1)
            includeSum = true;
        else if (ver == 2)
            includeSum = in.readBoolean();
    }
}
