package com.activequant.servicelayer.matlab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.activequant.archive.IArchiveFactory;
import com.activequant.archive.TSContainer;
import com.activequant.archive.hbase.HBaseArchiveFactory;
import com.activequant.dao.DaoException;
import com.activequant.dao.IDaoFactory;
import com.activequant.domainmodel.Instrument;
import com.activequant.domainmodel.MarketDataInstrument;
import com.activequant.domainmodel.SecurityChainByDate;
import com.activequant.domainmodel.TimeFrame;
import com.activequant.domainmodel.TimeStamp;
import com.activequant.servicelayer.matlab.reqhandlers.SynthUsedExpiry;
import com.activequant.utils.Date8Time6Parser;

/**
 * This facade is to be used by MATLAB users. Nothing else. All functionality
 * required by ML users will go in here.
 * 
 * 
 * @author ustaudinger
 * 
 */
public class MatlabServiceFacade {

    private IArchiveFactory archiveFactory;
    // private Date8Time6Parser dateParser = new Date8Time6Parser();
    private Logger log = Logger.getLogger(MatlabServiceFacade.class);
    private Date8Time6Parser d8t6p = new Date8Time6Parser();
    private Converters converter = new Converters();
    private IDaoFactory daoFactory;
    // manually add request handlers for synthetic fields. 
    private IRequestHandler[] reqHandlers;

    public MatlabServiceFacade() {
        this("localhost");
    }
    
    public MatlabServiceFacade(String zookeeperHost) {
        log.info("Instantiating new archive factory.");
        archiveFactory = new HBaseArchiveFactory(zookeeperHost);
        ApplicationContext appContext = new ClassPathXmlApplicationContext("fwspring.xml");
        daoFactory = (IDaoFactory) appContext.getBean("ibatisDao");
        reqHandlers = new IRequestHandler[]{new SynthUsedExpiry(daoFactory, archiveFactory)};
    }
    
    public MatlabServiceFacade(IArchiveFactory af, IDaoFactory dao) {
        log.info("Instantiating new archive factory.");
        archiveFactory = af;
        daoFactory = dao;
    }

    public String[] getInstrumentIds() throws DaoException {
        return daoFactory.instrumentDao().loadIDs();
    }

    public Instrument loadInstrument(String instrumentId) throws DaoException {
        return daoFactory.instrumentDao().load(instrumentId);
    }

    public MarketDataInstrument[] loadMarketDataInstruments(Instrument instrument) {
        return daoFactory.mdiDao().findFor(instrument);
    }

    /**
     * Fetches the instrument id for an mdi id.
     * 
     * @param mdiId
     * @return
     * @throws DaoException
     */
    public String getIID(String mdiId) throws DaoException {
        MarketDataInstrument mdi = daoFactory.mdiDao().load(mdiId);
        if (mdi != null) {
            return mdi.getInstrumentId();
        }
        return null;
    }

    public MarketDataInstrument loadMarketDataInstrument(String mdiId) throws DaoException {
        return daoFactory.mdiDao().load(mdiId);
    }

    public TimeSeriesContainer fetchTSData(TimeFrame tf, String[] marketInstrumentIds, String[] fieldNames,
            double startDate8Time6, Map<Parameter, Object> paramMap) throws Exception {
        return fetchTSData(tf, marketInstrumentIds, fieldNames, startDate8Time6, d8t6p.now().doubleValue(), paramMap);
    }

    public TimeSeriesContainer fetchTSData(TimeFrame tf, MarketDataInstrument[] marketInstruments, String[] fieldNames,
            double startDate8Time6, double endDate8Time6, Map<Parameter, Object> paramMap) throws Exception {
        String[] mdiIds = new String[marketInstruments.length];
        for(int i=0;i<marketInstruments.length;i++)
        {
            mdiIds[i] = marketInstruments[i].getId();
        }
            
        return fetchTSData(tf, mdiIds, fieldNames,
                startDate8Time6, endDate8Time6, paramMap);
    }
    
    public TimeSeriesContainer fetchTSData(TimeFrame tf, String[] marketInstrumentIds, String[] fieldNames,
            double startDate8Time6, double endDate8Time6, Map<Parameter, Object> paramMap) throws Exception {
        //
        if (paramMap == null)
            paramMap = new HashMap<Parameter, Object>();
        Date8Time6Parser parser = new Date8Time6Parser(); 

        //
        Map<String, Map<String, Map<TimeStamp, Double>>> dataMap = new HashMap<String, Map<String, Map<TimeStamp, Double>>>();
        List<TimeStamp> timeStamps = new ArrayList<TimeStamp>();
        for (int i = 0; i < marketInstrumentIds.length; i++) {
            String instrument = marketInstrumentIds[i];
            // get the instrument specific map.
            Map<String, Map<TimeStamp, Double>> instrumentMap;
            if (!dataMap.containsKey(instrument))
                dataMap.put(instrument, new HashMap<String, Map<TimeStamp, Double>>());
            instrumentMap = dataMap.get(instrument);

            for (int j = 0; j < fieldNames.length; j++) {
                String field = fieldNames[j];
                // get the field specific map
                Map<TimeStamp, Double> fieldMap;
                if (!instrumentMap.containsKey(field))
                    instrumentMap.put(field, new HashMap<TimeStamp, Double>());
                fieldMap = instrumentMap.get(field);
                TimeStamp t1 = new TimeStamp(parser.getNanoseconds(startDate8Time6));
                TimeStamp t2 = new TimeStamp(parser.getNanoseconds(endDate8Time6));
                
                ////////////// ordinary fields.
                TSContainer ts = null; 
                for(IRequestHandler h : reqHandlers){
                	if(h.handles(field)){
                		ts = h.handle(instrument, field, t1, t2);
                		break; 
                	}
                }                
                if(ts==null)
                	ts = archiveFactory.getReader(tf).getTimeSeries(instrument, field, t1, t2);
                ////////////// synthetic, custom fields.                 
                                
                if (ts.timeStamps.length > 0) {
                    for (int k = 0; k < ts.timeStamps.length; k++) {
                        TimeStamp timeStamp = ts.timeStamps[k];
                        Double value = ts.values[k];
                        // TODO: have to reduce the granularity.
                        if (!timeStamps.contains(timeStamp))
                            timeStamps.add(timeStamp);
                        fieldMap.put(timeStamp, value);
                    }
                }
            }
        }

        return converter.convertDataMap(timeStamps, dataMap, marketInstrumentIds, fieldNames, paramMap);

    }

    /**
     * can be used for testing ...
     * 
     * @return
     */
    public String facadeName() {
        return ("ServiceFacade by Ulrich.");
    }

    /**
     * Fetches data from start time to now(). delegates on to the other
     * fetchTSData method.
     * 
     * @param tf
     * @param marketInstrumentId
     * @param fieldName
     * @param startDate8Time6
     * @return
     * @throws Exception
     */
    public TimeSeriesContainer fetchTSData(TimeFrame tf, String marketInstrumentId, String fieldName,
            double startDate8Time6, Map<Parameter, Object> paramMap) throws Exception {
        return fetchTSData(tf, new String[] { marketInstrumentId }, new String[] { fieldName }, startDate8Time6, d8t6p
                .now().doubleValue(), paramMap);
    }

    public TimeSeriesContainer fetchTSData(TimeFrame tf, String marketInstrumentId, String fieldName,
            double startDate8Time6, double endDate8Time6, Map<Parameter, Object> paramMap) throws Exception {
        return fetchTSData(tf, new String[] { marketInstrumentId }, new String[] { fieldName }, startDate8Time6,
                endDate8Time6, paramMap);
    }

    public SecurityChainByDate loadSecChain(String chainId) throws Exception {
        return (SecurityChainByDate)daoFactory.securityChainDao().load(chainId);
    }
    
    
    /**
     * Used for basic and simple testing.
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        MatlabServiceFacade msf = new MatlabServiceFacade();
        Map<Parameter, Object> pMap = new HashMap<Parameter, Object>();
        pMap.put(Parameter.INTERPOLRULE, InterpolationRule.CARRY_FORWARD);
        TimeSeriesContainer data = msf.fetchTSData(TimeFrame.EOD, new String[] { "CSI_AD", "CSI_EURUSD" },
                new String[] { "CLOSE" }, 20000701000000.0, 20000710000000.0, pMap);

        msf.fetchTSData(TimeFrame.EOD, new String[] { "CSI_AD" }, new String[] { "OPEN", "CLOSE" }, 20000101000000.0,
                20000105000000.0, null);
        System.out.println("Done");
    }

}
