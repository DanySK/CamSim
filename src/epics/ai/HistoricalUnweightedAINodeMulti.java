package epics.ai;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import epics.camsim.core.TraceableObject;
import epics.camsim.core.TraceableObjectRepresentation;
import epics.common.IRegistration;
import epics.common.ITrObjectRepresentation;

public class HistoricalUnweightedAINodeMulti extends ActiveAINodeMulti {
	
    private static boolean DEBUG_CAM = true;

	/** How many historical points (x,y) to keep in order to judge 
	 * quantity of movement, that we use to calculate bids */
	private static final int NUM_OF_HISTORICAL_POINTS = 3;

	// Historical utility based fields
    /** Where this object has been over the last few timesteps */
    private Map<ITrObjectRepresentation, LinkedList<Point2D.Double>> historicalLocations = new HashMap<ITrObjectRepresentation, LinkedList<Point2D.Double>>();
    /** How many timesteps this object has been visible for */
	private Map<ITrObjectRepresentation, Integer> timestepsVisible = new HashMap<ITrObjectRepresentation, Integer>();
	
	/** How many timesteps in total objects are visible for (divide by counter for avg) */
	private double totalTSVisible = 0;
	/** How many objects have left our FOV (avg = totalTSVisible/tsVisibleCounter) */
	private int tsVisibleCounter = 0;
	
    public HistoricalUnweightedAINodeMulti(int comm, boolean staticVG, Map<String, Double> vg, IRegistration r){
    	super(comm, staticVG, vg, r); // Goes through to instantiateAINode()
    }

    @Override
    public void update() {
    	if(DECLINE_VISION_GRAPH)
    		this.updateVisionGraph();
    	
//    	double resRes =0;
//    	for(Double res : reservedResources.values()){
//			resRes +=res;
//		}
//    	double totRes = resRes + this.camController.getResources();
//    	System.out.println(this.camController.getName() + " resources reserved: " + resRes + " available: " + this.camController.getResources() + " total: " + totRes);

    	if(DEBUG_CAM) {
			String output = this.camController.getName();
			if(this.camController.isOffline()){
				output += " should be offline!! but";
			}
			output += " traces objects [real name] (identified as): ";    	
    	
			//    	ITrObjectRepresentation realITO;
			for (Map.Entry<List<Double>, ITrObjectRepresentation> kvp : tracedObjects.entrySet()) {
				String wrong = "NONE";
				String real = "" + kvp.getValue().getFeatures();
				if(wrongIdentified.containsValue(kvp.getValue())){
					//kvp.getValue is not real... find real...
					for(Map.Entry<ITrObjectRepresentation, ITrObjectRepresentation> kvpWrong : wrongIdentified.entrySet()){
						if(kvpWrong.getValue().equals(kvp.getValue())){
							wrong = "" + kvp.getValue().getFeatures();
							real = "" + kvpWrong.getKey().getFeatures();
							break;
						}
						else{
							wrong = "ERROR";
						}
					}
				}
				output = output + real + "(" + wrong + "); ";
			}
			System.out.println(output);
		}
    	
		// Store current point and get rid of an old one if necessary
		updateHistoricalPoints();
    	
    	updateReceivedDelay();
    	updateAuctionDuration();
    	
    	addedObjectsInThisStep = 0;
    	
        checkIfSearchedIsVisible();
        
        checkIfTracedGotLost();

        checkConfidences();
        
        printBiddings();
        
        checkBidsForObjects();       
        
        updateReservedResources();
        
        if(USE_BROADCAST_AS_FAILSAVE)
        	updateBroadcastCountdown();	
    }

	/** Store the current point for each visible object, and delete
	 *  the old one in the front of the queue if necessary. This allows
	 *  bids to be made later based on where the point has been. 
	 *  Also updates how many timesteps this object has been visible. */
	private void updateHistoricalPoints() {
		//List<ITrObjectRepresentation> objectsSeen = timestepsVisible.keySet().clone();

		// For every object, check for points. If nothing there, create a list first.
		for(ITrObjectRepresentation itro : this.camController.getVisibleObjects_bb().keySet()) {
			TraceableObjectRepresentation tor = (TraceableObjectRepresentation) itro;
			TraceableObject object = tor.getTraceableObject();
			LinkedList<Point2D.Double> pointsForObject = historicalLocations.get(itro);

			// If new object
			if (pointsForObject == null) {
				pointsForObject = new LinkedList<Point2D.Double>();
				historicalLocations.put(itro, pointsForObject);
			}

			if (pointsForObject.size() >= NUM_OF_HISTORICAL_POINTS) {
				pointsForObject.removeFirst();
			}
			
			Point2D.Double point = new Point2D.Double(object.getX(), object.getY());
			pointsForObject.add(point);

			System.out.println("\tPoints for object "+tor.getFeatures()+" are: ");
			for (Point2D.Double curPoint : pointsForObject) {
				System.out.println("\t\t"+curPoint);
			}
			System.out.println("\tQOM for object "+tor.getFeatures()+" is: "+getQuantityOfMovement(itro));

			if (! timestepsVisible.containsKey(itro)) {
				timestepsVisible.put(itro, 1); // First time seen
			} else {
				// Seen for one more timestep. Increment
				timestepsVisible.put(itro, timestepsVisible.get(itro) + 1);
			}
		}
		
		Iterator<ITrObjectRepresentation> iter = timestepsVisible.keySet().iterator();
		while (iter.hasNext()) {
			ITrObjectRepresentation itro = iter.next();
			if (! this.camController.getVisibleObjects_bb().containsKey(itro)) {
				// Object disappeared. Grab visible timesteps
				int visibleTS = timestepsVisible.get(itro);
				this.totalTSVisible += visibleTS;
				this.tsVisibleCounter++;
				iter.remove(); // Object is accounted for
				
				if (DEBUG_CAM) {
					System.out.println("\tObject "+itro.getFeatures()+" lost by "+this.camController.getName()+". ");
					System.out.println("\tTS visible: "+visibleTS+" and current avg: "+totalTSVisible/tsVisibleCounter
							+" ("+tsVisibleCounter+" objects seen so far)");
				}
			}
		}
	}

	private Double getQuantityOfMovement(ITrObjectRepresentation itro) {
		LinkedList<Point2D.Double> pointsForObject = historicalLocations.get(itro);
		double totalDistance = 0;

		// Not enough points to get a speed
		if (pointsForObject.size() < 2) {
			return null;
		}
		for (int i = 0; i < pointsForObject.size() - 1; i++) {
			Point2D.Double curPoint = pointsForObject.get(i);
			Point2D.Double nextPoint = pointsForObject.get(i+1);
			totalDistance += curPoint.distance(nextPoint);
		}
		// Mean of the distances between successive points, 
		// i.e. the mean speed between timesteps
		return totalDistance / ((double)pointsForObject.size()-1.0);
	}
    
	private Double getHistoryBasedBid(ITrObjectRepresentation itro) {
		// Formula is bid = avgTS * confidence
		// where avgTS is the average timesteps an object is present for
		throw new UnsupportedOperationException("Don't call this method yet!");
	}

}
