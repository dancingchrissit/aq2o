package com.activequant.backtesting;

import com.activequant.archive.TimeSeriesIterator;
import com.activequant.domainmodel.TimeStamp;
import com.activequant.domainmodel.Tuple;
import com.activequant.domainmodel.streaming.MarketDataEvent;
import com.activequant.domainmodel.streaming.MarketDataSnapshot;
import com.activequant.domainmodel.streaming.StreamEventIterator;
import com.activequant.interfaces.archive.IArchiveReader;

/**
 * 
 * converts a field to a stream. such as PX_SETTLE of daily data. 
 * 
 * @author ustaudinger
 *
 */
public class FieldToBidAskConverterStream extends StreamEventIterator<MarketDataEvent> {

    private String mdiId, tdiId, fieldName;  
    private TimeSeriesIterator streamIterator; 
    private double[] bid, ask, bidQ, askQ; 
    private double bidQuantity = 1000000, askQuantity = 1000000, bidOffset = -0.0001, askOffset = +0.0001;
     
    
    public FieldToBidAskConverterStream(String mdiId, String field, TimeStamp startTime, TimeStamp endTime, IArchiveReader archiveReader) throws Exception {
        this.mdiId = mdiId;
        this.tdiId = mdiId; 
        this.fieldName = field;
        this.streamIterator = archiveReader.getTimeSeriesStream(mdiId, fieldName, startTime, endTime);
        bid = new double[1];
        ask = new double[1];
        bidQ = new double[1];
        askQ = new double[1];          
    }
    
    public FieldToBidAskConverterStream(String mdiId, String tdiId, String field, TimeStamp startTime, TimeStamp endTime, IArchiveReader archiveReader) throws Exception {
        this.mdiId = mdiId;
        this.fieldName = field; 
        this.streamIterator = archiveReader.getTimeSeriesStream(mdiId, fieldName, startTime, endTime);
        bid = new double[1];
        ask = new double[1];
        bidQ = new double[1];
        askQ = new double[1];          
        this.tdiId = tdiId; 
    }
    
    @Override
    public boolean hasNext() {
       return streamIterator.hasNext();
    }

    @Override
    public MarketDataEvent next() {
        Tuple<TimeStamp, Double> valueMap = streamIterator.next();
        
 
        // fixing ... 
        bid = new double[1];
        ask = new double[1];
        bidQ = new double[1];
        askQ = new double[1];    

        // take the value and create a synthetic bid and ask out of  it.
        ask[0] = valueMap.getB()+this.getAskOffset();
        bid[0] = valueMap.getB()+this.getBidOffset();
        askQ[0] = this.getAskQuantity();
        bidQ[0] = this.getBidQuantity();
        
        MarketDataSnapshot mds = new MarketDataSnapshot();
        mds.setMdiId(this.mdiId);
        mds.setTdiId(this.tdiId);
        mds.setAskPrices(ask);
        mds.setBidPrices(bid);
        mds.setAskSizes(askQ);
        mds.setBidSizes(bidQ);
        mds.setTimeStamp(valueMap.getA());
        return mds; 

    }

	public double getBidQuantity() {
		return bidQuantity;
	}

	public void setBidQuantity(double bidQuantity) {
		this.bidQuantity = bidQuantity;
	}

	public double getAskQuantity() {
		return askQuantity;
	}

	public void setAskQuantity(double askQuantity) {
		this.askQuantity = askQuantity;
	}

	public double getBidOffset() {
		return bidOffset;
	}

	public void setBidOffset(double bidOffset) {
		this.bidOffset = bidOffset;
	}

	public double getAskOffset() {
		return askOffset;
	}

	public void setAskOffset(double askOffset) {
		this.askOffset = askOffset;
	}
}
