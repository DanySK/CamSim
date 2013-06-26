package epics.ai;

import java.util.List;

import java.util.Map;

import javax.sound.midi.ControllerEventListener;

import com.sun.xml.internal.bind.v2.runtime.unmarshaller.XsiNilLoader.Single;

import epics.camsim.core.TraceableObject;
import epics.common.AbstractAINode;
import epics.common.Coordinate2D;
import epics.common.IBanditSolver;
import epics.common.IRegistration;
import epics.common.ITrObjectRepresentation;
import epics.common.RandomNumberGenerator;
import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

public class AWASSAINodeMulti extends AbstractAINode {

//	private final List<TraceableObject> a;
	
	/**
	 * Creates an AI Node with active auction schedule from another, existing ai
	 * node
	 * 
	 * @param ai
	 *            the existing ai node
	 */
	public AWASSAINodeMulti(AbstractAINode ai) {
		super(ai);
	}

	/**
	 * Creates an AI Node with active auction schedule WITHOUT bandit solver for
	 * switching to another node automatically
	 * 
	 * @param comm
	 *            the used communication policy
	 * @param staticVG
	 *            if static vision graph or not
	 * @param vg
	 *            the initial vision graph
	 * @param r
	 *            the global registration component - can be null
	 * @param auctionDuration
	 *            the duration of auctions
	 * @param rg
	 *            the random number generator for this instance
	 */
	public AWASSAINodeMulti(boolean staticVG, Map<String, Double> vg, IRegistration r, int auctionDuration, RandomNumberGenerator rg) {
		super(staticVG, vg, r, auctionDuration, rg);
	}

	/**
	 * Creates an AI Node with active auction schedule WITH a bandit solver for
	 * switching to another node automatically
	 * 
	 * @param comm
	 *            the used communication policy
	 * @param staticVG
	 *            if static vision graph or not
	 * @param vg
	 *            the initial vision graph
	 * @param r
	 *            the global registration component - can be null
	 * @param auctionDuration
	 *            the duration of auctions
	 * @param rg
	 *            the random number generator for this instance
	 * @param bs
	 *            the bandit solver
	 */
	public AWASSAINodeMulti(boolean staticVG, Map<String, Double> vg, IRegistration r, int auctionDuration, RandomNumberGenerator rg, IBanditSolver bs) {
		super(staticVG, vg, r, auctionDuration, rg, bs);
	}

	/**
	 * Creates an AI Node with active auction schedule WITHOUT bandit solver for
	 * switching to another node automatically
	 * 
	 * @param comm
	 *            the used communication policy
	 * @param staticVG
	 *            if static vision graph or not
	 * @param vg
	 *            the initial vision graph
	 * @param r
	 *            the global registration component - can be null
	 * @param rg
	 *            the random number generator for this instance
	 */
	public AWASSAINodeMulti(boolean staticVG, Map<String, Double> vg, IRegistration r, RandomNumberGenerator rg) {
		super(staticVG, vg, r, rg);
	}

	/**
	 * Creates an AI Node with active auction schedule WITH a bandit solver for
	 * switching to another node automatically
	 * 
	 * @param comm
	 *            the used communication policy
	 * @param staticVG
	 *            if static vision graph or not
	 * @param vg
	 *            the initial vision graph
	 * @param r
	 *            the global registration component - can be null
	 * @param rg
	 *            the random number generator for this instance
	 * @param bs
	 *            the bandit solver
	 */
	public AWASSAINodeMulti(boolean staticVG, Map<String, Double> vg, IRegistration r, RandomNumberGenerator rg, IBanditSolver bs) {
		super(staticVG, vg, r, rg, bs);
	}

	private Coordinate2D toCameraSpace(final Coordinate2D pointPos){
		final Coordinate2D camPos = camController.getPostion();
		final double camAngle = -camController.getHeading();
		final double dx = pointPos.getX()-camPos.getX();
		final double dy = pointPos.getY()-camPos.getY();
		final double x = dx * cos(camAngle) + dy * sin(camAngle);
		final double y = -dx * sin(camAngle) + dy * cos(camAngle);
		return new Coordinate2D(x, y);
	}
	
	private Coordinate2D toGlobalSpace(final Coordinate2D pointPos){
		final Coordinate2D camPos = camController.getPostion();
		final double camAngle = -camController.getHeading();
		final double dx = pointPos.getX();
		final double dy = pointPos.getY();
		final double x = dx * cos(camAngle) - dy * sin(camAngle);
		final double y = dx * sin(camAngle) + dy * cos(camAngle);
		return new Coordinate2D(x+camPos.getX(), y+camPos.getY());
	}
	
	public void advertiseTrackedObjects() {
		// Active strategy means all objects are advertised every time
		for (ITrObjectRepresentation io : this.getAllTrackedObjects_bb().values()) {
			TraceableObject tio = io.getTraceableObject();
			callForHelp(io);
		}
	}
}