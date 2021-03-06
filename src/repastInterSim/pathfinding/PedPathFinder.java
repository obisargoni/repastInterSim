package repastInterSim.pathfinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.function.Predicate;
import java.util.stream.Collectors;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;

import repast.simphony.space.gis.Geography;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repastInterSim.agent.Ped;
import repastInterSim.environment.CrossingAlternative;
import repastInterSim.environment.GISFunctions;
import repastInterSim.environment.Junction;
import repastInterSim.environment.NetworkEdge;
import repastInterSim.environment.OD;
import repastInterSim.environment.Road;
import repastInterSim.environment.RoadLink;
import repastInterSim.environment.UnmarkedCrossingAlternative;
import repastInterSim.exceptions.RoutingException;
import repastInterSim.main.SpaceBuilder;

public class PedPathFinder {
	
	private Ped ped;
	
	private OD origin;
	private OD destination;
		
	private List<RoadLink> strategicPath;
	int tacticalHorizonLinks = 0;
	private Junction startPavementJunction;
	private Junction destPavementJunction;
	private AccumulatorRoute tacticalPath = new AccumulatorRoute();
	
	private Coordinate nextCrossingCoord;	

	public PedPathFinder(OD o, OD d, Geography<RoadLink> rlG, Network<Junction> orNetwork, Geography<OD> odG, Geography<Junction> paveG, Network<Junction> paveNetwork) {
		init(o, d, rlG, orNetwork, odG, paveG, paveNetwork);
	}
	
	public PedPathFinder(Ped p, Geography<RoadLink> rlG, Network<Junction> orNetwork, Geography<OD> odG, Geography<Junction> paveG, Network<Junction> paveNetwork) {
		this.ped = p;
		init(p.getOrigin(), p.getDestination(), rlG, orNetwork, odG, paveG, paveNetwork);
	}
	
	private void init(OD o, OD d, Geography<RoadLink> rlG, Network<Junction> orNetwork, Geography<OD> odG, Geography<Junction> paveG, Network<Junction> paveNetwork) {
		this.origin = o;
		this.destination = d;
				
		planStrategicPath(this.origin.getGeom().getCoordinate(), this.destination.getGeom().getCoordinate(), rlG, orNetwork, odG, paveG, paveNetwork);
	}
	
	public void step() {
		
		// Only update the accumulator path if the ped agent is walking along a road linked to the strategic path,
		// ie not crossing over another road
		if (pedOnStrategicPathRoadLink()) {
			this.tacticalPath.step();
		}
	}
	
	/**
	 * Initialise a new road link routing object that can be used to find a path along a topological road network.
	 * Use this to identify the shortest path through the network and assign this path to this classes' strategic path attribute.
	 * 
	 */
	public void planStrategicPath(Coordinate oC, Coordinate dC, Geography<RoadLink> rlG, Network<Junction> orNetwork, Geography<OD> odG, Geography<Junction> paveG, Network<Junction> paveNetwork) {
		RoadNetworkRoute rnr = new RoadNetworkRoute(oC, dC, rlG, orNetwork, odG);
		
		// initialise object to record the start and end pavement junctions of the route
		Junction[] routeEnds = null;
		
		// Find shortest path using road network route
		try {
			routeEnds = rnr.setRoadLinkRoute(paveG, paveNetwork);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Get path of road links and set this as the strategic path
		this.strategicPath = rnr.getRoadsX();
		
		
		this.startPavementJunction = routeEnds[0];
		this.destPavementJunction = routeEnds[1];
	}
	
	/*
	 * Set the next tactical coordinate. If there are no coordinates in the tactical path, plan a new tactical path. Otherwise get the next coordinate
	 * in the tactical path.
	 * 
	 * @param gSPM
	 * 			The map from integers used to indicate the road user priority of grid cells to the perceived cost of moving through those grid cells. Used for routing on a grid.
	 * @param tacticalOriginCoord
	 * 			The start coordinate of the tactical route.
	 */
	public void updateTacticalPath() {
		
		// First update the strategic path by removing the links that formed part of the previous tactical planning horizon
		for (int i = 0; i < tacticalHorizonLinks; i++) {
			this.strategicPath.remove(0);
		}
		
		// If no tactical path has been set use the strategic path start junction, otherwise set the start junction as the end junction of previous tactical path
		Junction startJunction = null;
		if (this.tacticalPath.isBlank()) {
			startJunction = this.startPavementJunction;
		}
		else {
			startJunction = this.tacticalPath.getCurrentTA().getOutsideJunction();
		}
		
		// Initialise Accumulator Route that agent will use to navigate along the planning horizon, and update the number of links in the tactical planning horizon
		tacticalHorizonLinks = planTacticaAccumulatorPath(SpaceBuilder.pavementNetwork, SpaceBuilder.caGeography, SpaceBuilder.roadGeography, this.ped, this.strategicPath, startJunction, this.destPavementJunction);
	
    }
	
	/*
	 * Plan a tactical level path using the accumulator crossing choice path finding model.
	 */
	public int planTacticaAccumulatorPath(Network<Junction> pavementNetwork, Geography<CrossingAlternative> caG, Geography<Road> rG, Ped p, List<RoadLink> sP, Junction currentJ, Junction destJ) {
		
		// Calculate number of links in planning horizon
		int nLinks = getNLinksWithinAngularDistance(sP, p.getpHorizon());
		
		// Get length of the planning horizon, this is used in the accumulator route
		double pHLength = 0;
		for (int i = 0; i<nLinks; i++) {
			pHLength += sP.get(i).getGeom().getLength();
		}
		
		boolean endOfJourney = false;
		if (nLinks == sP.size()) {
			endOfJourney = true;
		}
		
		// First identify tactical route alternatives
		List<TacticalAlternative> trs = new ArrayList<TacticalAlternative>();
		if (endOfJourney) {
			trs = destinationTacticalAlternatives(pavementNetwork, sP, nLinks, currentJ, destJ, caG, rG, p);
		}
		else {
			trs = tacticalAlternatives(pavementNetwork, sP, nLinks, currentJ, destJ, caG, rG, p);
		}
		
		
		// Sort routes based on the length of the path to the end of tactical horizon
		List<String> strategiRoadLinkIDs = sP.stream().map(rl->rl.getPedRLID()).collect(Collectors.toList());
		PavementRoadLinkTransformer<Junction> transformer = new PavementRoadLinkTransformer<Junction>(strategiRoadLinkIDs, Double.MAX_VALUE);
		List<Double> pathLengths = trs.stream().map(tr -> NetworkPath.getPathLength(tr.getRoutePath(), transformer)).collect(Collectors.toList());
		
		// Identify default and chosen tactical route alternatives
		// Default tactical route is the one with the fewest primary crossings
		TacticalAlternative defaultTR = trs.get(pathLengths.indexOf(Collections.min(pathLengths)));
		
		// Choose the target tactical alternative differently depending on whether end of route has been reached
		TacticalAlternative chosenTR = null;
		if (endOfJourney) {
			// Target tactical alternative is one with dest junction as end junction
			chosenTR = trs.stream().filter(tr -> tr.getEndJunction().getFID().contentEquals(destJ.getFID())).collect(Collectors.toList()).get(0);
			
			// Include the destination coordinate in the tactical route
			chosenTR.setDestinationCoordinate(this.destination.getGeom().getCoordinate());
			// Used to keep pedestrian agent end end junction
			defaultTR.setRecurringEndJunction(true);
		}
		else {
			// Default to choosing alternative with fewest primary crossings required to complete tactical horizon
			chosenTR = defaultTR;
		}
		
		// Initialise Accumulator route with the chosen tactical route also set as the default.
		AccumulatorRoute accRoute = new AccumulatorRoute(p, pHLength, defaultTR, chosenTR);
		
		this.tacticalPath = accRoute;
		
		return nLinks;
	}
	
	/*
	 * Create a tactical alternative which contains the route from the current junction to the end junction.
	 */
	public static TacticalAlternative setupTacticalAlternativeRoute(NetworkPath<Junction> nP, Junction eJ, Junction currentJ) {
		
		// Now that paths identified, initialise the tactical route object
		TacticalAlternative tr = new TacticalAlternative(nP, currentJ, eJ);
		tr.setPathToEnd();
		// Update the current junction so that the first junction the ped agent walks towards is not their current junction but the next one in the route
		tr.updateCurrentJunction();
		
		return tr;
	}
	
	/*
	 * Create a tactical alternative which contains the route from the current jucntion to the end junction, from the end junction to the outside junction and from the outside
	 * junction to the final destination.
	 */
	public static TacticalAlternative setupTacticalAlternativeRoute(NetworkPath<Junction> nP, List<RoadLink> sP, Junction eJ, List<Junction> outsideJunctions, Junction currentJ, Junction destJ) {
		
		// Get the tactical alternative with the route to the end junction planned
		TacticalAlternative tr = new TacticalAlternative(nP, currentJ, eJ);
		tr.setPathToEnd();

		// Get path from end junction to the junction at the start of the first link outside the tactical planning horizon
		List<RepastEdge<Junction>> pathToOutside = new ArrayList<RepastEdge<Junction>>();
		Junction outsideJunction = null;
		double minLength = Double.MAX_VALUE;
				
		// Calculate path from the end junction to each possible outside junction. Select the shortest path that does not involve a primary crossing
		// This is the path that stays on the same side of the road and moves to the start of the next route section
		Predicate<Junction> nodeFilter = n -> n.getjuncNodeID().contentEquals(eJ.getjuncNodeID());
		for (Junction j: outsideJunctions) {
			List<Stack<RepastEdge<Junction>>> edgePaths = nP.getSimplePaths(eJ, j, nodeFilter);
			
			// Loop through these paths and find the shortest one that does not make a primary crossing
			for (Stack<RepastEdge<Junction>> edgePath : edgePaths) {
				if (containsPrimaryCrossing(edgePath, sP) == false) {
					double pathLength = nP.getPathLength(edgePath);
					if (pathLength < minLength) {
						pathToOutside = edgePath;
						minLength = pathLength;
						outsideJunction = j;
					}
				}
			}
		}
		
		tr.setPathEndToOutside(pathToOutside);
		tr.setOutsideJunction(outsideJunction);
		
		// If the destination junction is known, calculate the path from the last junction added to the tactical route to the destination junction
		// This is recorded separately as the path required to complete the journey
		if (destJ != null) {
			tr.setAlternativeRemainderPath(nP.getShortestPath(outsideJunction, destJ));
		}
		
		// Update the current junction so that the first junction the ped agent walks towards is not their current junction but the next one in the route
		tr.updateCurrentJunction();
		
		return tr;
	}
	
	/*
	 * Sets up the tactical alternative which includes only the route to the end junction. If the route requires a primary crossing to reach the end junction identify the possible crossing alternatives along the tactical
	 * route and add these to the tactical alternative.
	 */
	public static TacticalAlternative setupTacticalAlternative(NetworkPath<Junction> nP, List<RoadLink> sP, List<RoadLink> tSP, Junction eJ, Junction currentJ, Geography<CrossingAlternative> caG, Geography<Road> rG, Ped p) {
		
		TacticalAlternative tr = setupTacticalAlternativeRoute(nP, eJ, currentJ);
		
		// Finally identify the crossing alternatives available in order to complete this tactical route
		// If chosen to cross road, identify crossing options and initialise accumulator route
		List<CrossingAlternative> cas = new ArrayList<CrossingAlternative>();
		int primaryCrossingParity = RoadNetworkRoute.calculateRouteParity(currentJ.getGeom().getCoordinate(), eJ.getGeom().getCoordinate(), sP);
		if (primaryCrossingParity == 1) {
			// Get crossing alternatives within planning horizon
			cas = getCrossingAlternatives(caG, tSP, p, rG);
		}
		
		tr.setCrossingAlternatives(cas);
		
		return tr;
	}
	
	/*
	 * Sets up the tactical alternative route. If the route requires a primary crossing to reach the end junction identify the possible crossing alternatives along the tactical
	 * route and add these to the tactical alternative.
	 */
	public static TacticalAlternative setupTacticalAlternative(NetworkPath<Junction> nP, List<RoadLink> sP, List<RoadLink> tSP, Junction eJ, List<Junction> outsideJunctions, Junction currentJ, Junction destJ, Geography<CrossingAlternative> caG, Geography<Road> rG, Ped p) {
		
		TacticalAlternative tr = setupTacticalAlternativeRoute(nP, sP, eJ, outsideJunctions, currentJ, destJ);
		
		// Finally identify the crossing alternatives available in order to complete this tactical route
		// If chosen to cross road, identify crossing options and initialise accumulator route
		List<CrossingAlternative> cas = new ArrayList<CrossingAlternative>();
		int primaryCrossingParity = RoadNetworkRoute.calculateRouteParity(currentJ.getGeom().getCoordinate(), eJ.getGeom().getCoordinate(), sP);
		if (primaryCrossingParity == 1) {
			// Get crossing alternatives within planning horizon
			cas = getCrossingAlternatives(caG, tSP, p, rG);
		}
		
		tr.setCrossingAlternatives(cas);
		
		return tr;
	}
	
	
	public static List<TacticalAlternative> tacticalAlternatives(Network<Junction> pavementNetwork, List<RoadLink> sP, int tacticalNLinks, Junction currentJ, Junction destJ, Geography<CrossingAlternative> caG, Geography<Road> rG, Ped p) {
		// Get road link ID of link at end of planning horizon and first strategic path road link outside of planning horizon
		RoadLink rlEndHorz = sP.get(tacticalNLinks-1);
		RoadLink rlOutHorz = sP.get(tacticalNLinks);
		
		// Get horizon junctions
		HashMap<String, List<Junction>> horizonJunctions = tacticalHorizonJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		// get path to each of the junctions at the end of the planning horizon
		List<Junction> endJunctions = horizonJunctions.get("end");
		
		NetworkPath<Junction> nP = new NetworkPath<Junction>(pavementNetwork);
		
		// For each of the end junctions create a TacticalRoute object representing the route via this junction
		List<TacticalAlternative> tacticalRoutes = new ArrayList<TacticalAlternative>();
		for (Junction eJ: endJunctions) {
			TacticalAlternative tr = setupTacticalAlternative(nP, sP, sP.subList(0, tacticalNLinks), eJ, horizonJunctions.get("outside"), currentJ, destJ, caG, rG, p);
			tacticalRoutes.add(tr);
		}
		return tacticalRoutes;
	}
	
	/*
	 * Alternative method for setting up the tactical route alternatives to be use when the destination lies within the tactical planning horizon. In this case
	 * a different method is required for setting up the tactical alternatives that includes the destination within the route and a default option that doesn't involde crossing
	 * or progressing past the strategic path as it is assumed the ped agent does not have a tactical route choice at this stage, ie the ped is committed to walking to its destination.
	 */
	public static List<TacticalAlternative> destinationTacticalAlternatives(Network<Junction> pavementNetwork, List<RoadLink> sP, int tacticalNLinks, Junction currentJ, Junction destJ, Geography<CrossingAlternative> caG, Geography<Road> rG, Ped p) {
		
		// Find the default end junction by finding the junctions at the end of the strategic path and selecting the one that is not the destination junction
		Junction nonDestJ = null;
		String endNodeID = destJ.getjuncNodeID();
		RoadLink endRL = sP.get(sP.size()-1);
		HashMap<String, List<Junction>> horizonJunctions = tacticalHorizonJunctions(pavementNetwork, endRL, endNodeID);
		List<Junction> endJunctions = horizonJunctions.get("end");
		List<Junction> defaultJunctions = endJunctions.stream().filter(j -> !j.getFID().contentEquals(destJ.getFID())).collect(Collectors.toList());
		assert defaultJunctions.size() == 1;
		nonDestJ = defaultJunctions.get(0);
		
		NetworkPath<Junction> nP = new NetworkPath<Junction>(pavementNetwork);
		
		// Set up tactical alternative that includes the destination coordinate
		TacticalAlternative targetTA = setupTacticalAlternative(nP, sP, sP, destJ, currentJ, caG, rG, p);
		
		// Now set up the tactical alternative to the other end junction
		TacticalAlternative nonDestTA = setupTacticalAlternative(nP, sP, sP, nonDestJ, currentJ, caG, rG, p);
		
		List<TacticalAlternative> tacticalRoutes = new ArrayList<TacticalAlternative>();
		tacticalRoutes.add(targetTA);
		tacticalRoutes.add(nonDestTA);
		
		return tacticalRoutes;
	}
	
	private static boolean containsPrimaryCrossing(List<RepastEdge<Junction>> path, List<RoadLink> sP) {
		boolean cPC = false;
		for (RepastEdge<Junction> e: path) {
			NetworkEdge<Junction> ne = (NetworkEdge<Junction>) e;
			for (RoadLink rl : sP) {
				if (rl.getPedRLID().contentEquals(ne.getRoadLink().getPedRLID())) {
					cPC = true;
				}
			}
		}		
		return cPC;
	}
	
	/*
	 * Get the pavement network junctions that are at the end of the tactical planning horizon.
	 * 
	 */
	public static List<Junction> tacticalHorizonEndJunctions(Network<Junction> pavementNetwork, RoadLink rlEndHorz, RoadLink rlOutHorz) {
		
		HashMap<String, List<Junction>> tacticalEndJunctions = tacticalHorizonJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		return tacticalEndJunctions.get("end");
	}
	
	/*
	 * Get the pavement network junctions that are at the start of the first link beyond the tactical planning horizon.
	 * 
	 */
	public static List<Junction> tacticalHorizonOutsideJunctions(Network<Junction> pavementNetwork, RoadLink rlEndHorz, RoadLink rlOutHorz) {
		
		HashMap<String, List<Junction>> tacticalEndJunctions = tacticalHorizonJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		return tacticalEndJunctions.get("outside");
	}
	
	/*
	 * Get the pavement network junctions that at the intersection between the final road link in the tactical planning horizin and
	 * the next link in the strategic path that lies outside the tactical planning horizon.
	 * 
	 * Choose whether to return the junctions connected to the link at the end of the horizon or connected to the next link beyong the horizon.
	 * 
	 * @param Network<Junction> pavementNetwork
	 * 			The network of pavement junctions used to connect pedestrian pavements
	 * @param RoadLink rlEndHorz
	 * 			The road link at the end of the tactical planning horizon
	 * @param RoadLink rlOutHorz
	 * 			The road link on the other side of the tactical planning horizon
	 * @param boolean outside
	 * 			Selects whether to return the junctions at the end of the planning horizon or outside the planning horizon at the start of the next link
	 */
	public static HashMap<String, List<Junction>> tacticalHorizonJunctions(Network<Junction> pavementNetwork, RoadLink rlEndHorz, RoadLink rlOutHorz) {
		
		// First get the road network node id connecting these links
		String nodeID = connectingNodeID(rlEndHorz, rlOutHorz);
		
		// Get the pavement junctions linked to this road node
		List<Junction> pavementJunctions =  roadNodePavementJunctions(pavementNetwork, nodeID);

		// Loop over pavement junctions and select those touching the road link at the end of the planning horizon
		HashMap<String, List<Junction>> tacticalEndJunctions = new HashMap<String, List<Junction>>();
		tacticalEndJunctions.put("end", new ArrayList<Junction>());
		tacticalEndJunctions.put("outside", new ArrayList<Junction>());
		for (Junction j: pavementJunctions) {
			if (j.getv1rlID().contentEquals(rlEndHorz.getPedRLID()) | j.getv2rlID().contentEquals(rlEndHorz.getPedRLID())) {
				tacticalEndJunctions.get("end").add(j);
			}
			if (j.getv1rlID().contentEquals(rlOutHorz.getPedRLID()) | j.getv2rlID().contentEquals(rlOutHorz.getPedRLID())) {
				tacticalEndJunctions.get("outside").add(j);
			}
		}
		
		return tacticalEndJunctions;
	}
	
	/*
	 * Get the pavement network junctions around the input node id. Group these junctions according to whether they lie on the input road link or not.
	 * 
	 * @param Network<Junction> pavementNetwork
	 * 			The network of pavement junctions used to connect pedestrian pavements
	 * @param RoadLink rlEndHorz
	 * 			The road link at the end of the tactical planning horizon
	 * @param String endNodeID
	 * 			The road node to get pavement junctions for
	 */
	public static HashMap<String, List<Junction>> tacticalHorizonJunctions(Network<Junction> pavementNetwork, RoadLink rl, String endNodeID) {
		
		// Get the pavement junctions linked to this road node
		List<Junction> pavementJunctions =  roadNodePavementJunctions(pavementNetwork, endNodeID);

		// Loop over pavement junctions and select those touching the road link at the end of the planning horizon
		HashMap<String, List<Junction>> tacticalEndJunctions = new HashMap<String, List<Junction>>();
		tacticalEndJunctions.put("end", new ArrayList<Junction>());
		tacticalEndJunctions.put("outside", new ArrayList<Junction>());
		for (Junction j: pavementJunctions) {
			if (j.getv1rlID().contentEquals(rl.getPedRLID()) | j.getv2rlID().contentEquals(rl.getPedRLID())) {
				tacticalEndJunctions.get("end").add(j);
			}
		}
		
		return tacticalEndJunctions;
	}
	
	/*
	 * Identify the crossing alternatives that lie on the given road links. Prepare these crossing alternatives
	 * for use in the accumulator choice model.
	 * 
	 * @param Geography<CrossingAlternative> caG
	 * 		The geography containing all crossing alternative objects
	 * @param List<RoadLink> rls
	 * 		The road links to identify crossing alternatives along, typically the tactical planning horizon section of the strategic path.
	 * @param Ped p
	 * 		The pedestrian agent perceiving the crossing alternatives
	 * @param Geography<Road> rG
	 * 		The Geography containing Road objects
	 */
	public static List<CrossingAlternative> getCrossingAlternatives(Geography<CrossingAlternative> caG, List<RoadLink> rls, Ped p, Geography<Road> rG){
		
		List<CrossingAlternative> cas = new ArrayList<CrossingAlternative>();
		
		// Loop through open road sp section road links and crossing alternatives and add any crossing alternatives that match the road link id to the list
		for (RoadLink rl: rls) {
			for (CrossingAlternative ca: caG.getAllObjects()) {
				if (ca.getRoadLinkID().contentEquals(rl.getFID())) {
					// Add to list
					cas.add(ca);
				}
			}
		}
		
		// Add unmarked crossing alternative to lsit
		// Don't set road for unmarked crossings
		UnmarkedCrossingAlternative caU = new UnmarkedCrossingAlternative();
		caU.setPed(p);
		caU.setRoadGeography(rG);
		caU.setStrategicPathSection(rls);
		cas.add(caU);
		
		return cas;		
	}
	
	public static int getNLinksWithinAngularDistance(List<RoadLink> sP, double angDistThreshold) {
		// Initialise number of links in planning horizon as one (current link always in planning horizon)
		int nLinks = 1;
		
		// Initialise ang dist
		double angDist = 0.0;
		
		// Loop through road links in strategic path adding up the angular distance between each one
		for (int i = 0; i < sP.size() - 1; i++) {
			RoadLink rl1 = sP.get(i);
			RoadLink rl2 = sP.get(i+1);
			
			// Calculate angular distance to next road link
			angDist += GISFunctions.angleBetweenConnectedLineStrings((LineString) rl1.getGeom(), (LineString) rl2.getGeom());
			
			// If distance within threshold, add next link to horizon
			if (angDist > angDistThreshold) {
				break;
			}
			
			// If angular distance threshold not exceeded add 1 to number of links within horizon
			nLinks++;

		}
		return nLinks;
	}
	
	public static List<RoadLink> getLinksWithinAngularDistance(List<RoadLink> sP, double angDistThreshold){		
		// Calculate number of links that are within planning horizon
		int nLinks = getNLinksWithinAngularDistance(sP, angDistThreshold);
		
		// use this to get get planning horizon as list of links
		return sP.subList(0, nLinks);
	}
	
	/*
	 * Used to check whether the pedestrian agents is currently walking on a road
	 * polygon alongside a link in the strategic path or not.
	 * 
	 * When a pedestrian agent is making a secondary crossing it is not on a road beside
	 * a strategic path link.
	 */
	public Boolean pedOnStrategicPathRoadLink() {
		Boolean pedOnSLink = false;
		Road r = this.ped.getCurrentRoad();
		for (RoadLink rl: this.strategicPath) {
			if (rl.getFID().contentEquals(r.getRoadLinkID())) {
				pedOnSLink = true;
			}
		}
		return pedOnSLink;
	}
	
	/*
	 * Returns the ID of the road network node connecting two road links. Returns null if there isn't a shared node.
	 */
	public static String connectingNodeID(RoadLink rl1, RoadLink rl2) {
		
		// Find the common node
		if (rl1.getMNodeFID().contentEquals(rl2.getMNodeFID()) | rl1.getMNodeFID().contentEquals(rl2.getPNodeFID())) {
			return rl1.getMNodeFID();
		}
		else if (rl1.getPNodeFID().contentEquals(rl2.getPNodeFID()) | rl1.getPNodeFID().contentEquals(rl2.getMNodeFID())) {
			return rl1.getPNodeFID();
		}
		else {
			return null;
		}
	}
	
	/*
	 * Given a road node ID return the pavement network junctions associated to this node. These are the pavement junctions
	 * around a road network node.
	 */
	public static List<Junction> roadNodePavementJunctions(Network<Junction> pavementNetwork, String roadNodeID) {
		 List<Junction> nodeJunctions = new ArrayList<Junction>();
		 for(Junction j:pavementNetwork.getNodes()) {
			 if (j.getjuncNodeID().contentEquals(roadNodeID)) {
				 nodeJunctions.add(j);
			 }
		 }
		 return nodeJunctions;
	}
	
	
	public List<RoadLink> getStrategicPath() {
		return this.strategicPath;
	}
	
	public AccumulatorRoute getTacticalPath() {
		return this.tacticalPath;
	}
	
	public Coordinate getNextCrossingCoord() {
		return nextCrossingCoord;
	}
	
	public void setNextCrossingCoord(Coordinate c) {
		this.nextCrossingCoord = c;
	}
	
	public OD getOrigin() {
		return origin;
	}

	public void setOrigin(OD origin) {
		this.origin = origin;
	}
	
	public String getCurrentRoadLinkID(Coordinate c) {
		
		String currentRoadLinkID = null;
		
		// For pedestrian ODs that are close to one another on the same road link the currentRoadLink is null
		// In this case pedestrian identifies the road link to estimate vehicle priority costs by using their current location
		if (this.strategicPath.isEmpty()) {
			Road currentRoad;
			try {
				currentRoad = GISFunctions.getCoordinateRoad(c);
	    		currentRoadLinkID = currentRoad.getRoadLinkID();
			} catch (RoutingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else {
			currentRoadLinkID = this.strategicPath.get(0).getFID();
		}
		
		return currentRoadLinkID;
	}

	public Junction getStartPavementJunction() {
		return startPavementJunction;
	}

	
	public Junction getDestPavementJunction() {
		return destPavementJunction;
	}	
	
}
