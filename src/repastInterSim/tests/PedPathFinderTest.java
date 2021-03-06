package repastInterSim.tests;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.vividsolutions.jts.geom.Coordinate;

import repast.simphony.context.Context;
import repast.simphony.context.DefaultContext;
import repast.simphony.context.space.gis.GeographyFactoryFinder;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.space.gis.Geography;
import repast.simphony.space.gis.GeographyParameters;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repastInterSim.agent.Ped;
import repastInterSim.environment.CrossingAlternative;
import repastInterSim.environment.GISFunctions;
import repastInterSim.environment.Junction;
import repastInterSim.environment.NetworkEdge;
import repastInterSim.environment.NetworkEdgeCreator;
import repastInterSim.environment.OD;
import repastInterSim.environment.PedObstruction;
import repastInterSim.environment.Road;
import repastInterSim.environment.RoadLink;
import repastInterSim.environment.SpatialIndexManager;
import repastInterSim.environment.contexts.CAContext;
import repastInterSim.environment.contexts.JunctionContext;
import repastInterSim.environment.contexts.PedObstructionContext;
import repastInterSim.environment.contexts.PedestrianDestinationContext;
import repastInterSim.environment.contexts.RoadContext;
import repastInterSim.environment.contexts.RoadLinkContext;
import repastInterSim.main.GlobalVars;
import repastInterSim.main.IO;
import repastInterSim.pathfinding.PedPathFinder;
import repastInterSim.pathfinding.RoadNetworkRoute;
import repastInterSim.pathfinding.NetworkPath;
import repastInterSim.pathfinding.TacticalAlternative;

class PedPathFinderTest {
	
	Context<Object> context = new DefaultContext<Object>();;
	Geography<Object> geography; 
	Geography<Road> roadGeography = null;
	Geography<OD> odGeography = null;
	Geography<CrossingAlternative> caGeography = null;
	Geography<PedObstruction> pedObstructGeography = null;
	Geography<RoadLink> roadLinkGeography = null;
	Geography<RoadLink> pavementLinkGeography = null;
	Network<Junction> roadNetwork = null;
	Context<Junction> pavementJunctionContext = null;
	Geography<Junction> pavementJunctionGeography = null;
	Network<Junction> pavementNetwork = null;
	
	
	String testGISDir = ".//data//test_gis_data//";
	String pedestrianRoadsPath = null;
	String vehicleRoadsPath = null;
	String roadLinkPath = null;
	String pavementLinkPath = null;
	String pedJPath = null;
	String serialisedLookupPath = null;
	
	void setUpProperties() throws IOException {
		IO.readProperties();
	}
	
	void setUpObjectGeography() {
		GeographyParameters<Object> geoParams = new GeographyParameters<Object>();
		geography = GeographyFactoryFinder.createGeographyFactory(null).createGeography(GlobalVars.CONTEXT_NAMES.MAIN_GEOGRAPHY, context, geoParams);
		geography.setCRS(GlobalVars.geographyCRSString);
		context.add(geography);
	}
	
	void setUpRoads() throws Exception {
		pedestrianRoadsPath = testGISDir + "topographicAreaPedestrian.shp";
		vehicleRoadsPath = testGISDir + "topographicAreaVehicle.shp";
		serialisedLookupPath = testGISDir + "road_link_roads_cache.serialised";
		
		// Get road geography
		Context<Road> testRoadContext = new RoadContext();
		GeographyParameters<Road> GeoParamsRoad = new GeographyParameters<Road>();
		roadGeography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("testRoadGeography", testRoadContext, GeoParamsRoad);
		roadGeography.setCRS(GlobalVars.geographyCRSString);

		
		// Load vehicle origins and destinations
		try {
			GISFunctions.readShapefile(Road.class, vehicleRoadsPath, roadGeography, testRoadContext);
			GISFunctions.readShapefile(Road.class, pedestrianRoadsPath, roadGeography, testRoadContext);
		} catch (MalformedURLException | FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		SpatialIndexManager.createIndex(roadGeography, Road.class);

	}
	
	Geography<RoadLink> setUpLinks(String roadLinkFile) throws MalformedURLException, FileNotFoundException {
		roadLinkPath = testGISDir + roadLinkFile;
		
		// Initialise test road link geography and context
		Context<RoadLink> roadLinkContext = new RoadLinkContext();
		GeographyParameters<RoadLink> GeoParams = new GeographyParameters<RoadLink>();
		Geography<RoadLink> rlG = GeographyFactoryFinder.createGeographyFactory(null).createGeography("roadLinkGeography", roadLinkContext, GeoParams);
		rlG.setCRS(GlobalVars.geographyCRSString);
				
		GISFunctions.readShapefile(RoadLink.class, roadLinkPath, rlG, roadLinkContext);
		SpatialIndexManager.createIndex(rlG, RoadLink.class);
		
		return rlG;
	}
	
	void setUpRoadLinks(String roadLinkFile) throws Exception {
		roadLinkGeography = setUpLinks(roadLinkFile);
	}
	
	void setUpRoadLinks() throws Exception {
		setUpRoadLinks("mastermap-itn RoadLink Intersect Within with orientation.shp");
	}
	
	void setUpPavementLinks(String linkFile) throws MalformedURLException, FileNotFoundException {
		pavementLinkGeography = setUpLinks(linkFile);
	}
		
	void setUpODs(String odFile) throws MalformedURLException, FileNotFoundException {
		
		// Initialise OD context and geography
		Context<OD> ODContext = new PedestrianDestinationContext();
		GeographyParameters<OD> GeoParamsOD = new GeographyParameters<OD>();
		odGeography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("testODGeography", ODContext, GeoParamsOD);
		odGeography.setCRS(GlobalVars.geographyCRSString);
		
		// Load vehicle origins and destinations
		String testODFile = testGISDir + odFile;
		GISFunctions.readShapefile(OD.class, testODFile, odGeography, ODContext);
		SpatialIndexManager.createIndex(odGeography, OD.class);
	}
	
	void setUpPedObstructions() throws MalformedURLException, FileNotFoundException {
		// Ped Obstruction context stores GIS linestrings representing barriers to pedestrian movement
		Context<PedObstruction> pedObstructContext = new PedObstructionContext();
		GeographyParameters<PedObstruction> GeoParams = new GeographyParameters<PedObstruction>();
		pedObstructGeography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("pedObstructGeography", pedObstructContext, GeoParams);
		pedObstructGeography.setCRS(GlobalVars.geographyCRSString);
		
		
		// Load ped obstructions data
		String testPedObstructFile = testGISDir + "boundaryPedestrianVehicleArea.shp";
		GISFunctions.readShapefile(PedObstruction.class, testPedObstructFile, pedObstructGeography, pedObstructContext);
		SpatialIndexManager.createIndex(pedObstructGeography, PedObstruction.class);
	}
	
	void setUpCrossingAlternatives() throws MalformedURLException, FileNotFoundException {
		// Ped Obstruction context stores GIS linestrings representing barriers to pedestrian movement
		Context<CrossingAlternative> caContext = new CAContext();
		GeographyParameters<CrossingAlternative> GeoParams = new GeographyParameters<CrossingAlternative>();
		caGeography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("caGeography", caContext, GeoParams);
		caGeography.setCRS(GlobalVars.geographyCRSString);
		
		
		// Load ped obstructions data
		String testCAFile = testGISDir + "crossing_lines.shp";
		GISFunctions.readShapefile(CrossingAlternative.class, testCAFile, caGeography, caContext);
		SpatialIndexManager.createIndex(caGeography, CrossingAlternative.class);
	}
	
	void setUpRoadNetwork(boolean isDirected) {
		Context<Junction> junctionContext = new JunctionContext();
		GeographyParameters<Junction> GeoParamsJunc = new GeographyParameters<Junction>();
		Geography<Junction> junctionGeography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("junctionGeography", junctionContext, GeoParamsJunc);
		junctionGeography.setCRS(GlobalVars.geographyCRSString);
		
		NetworkBuilder<Junction> builder = new NetworkBuilder<Junction>(GlobalVars.CONTEXT_NAMES.ROAD_NETWORK,junctionContext, isDirected);
		builder.setEdgeCreator(new NetworkEdgeCreator<Junction>());
		roadNetwork = builder.buildNetwork();
		
		GISFunctions.buildGISRoadNetwork(roadLinkGeography, junctionContext, junctionGeography, roadNetwork);
	}
	
	void setUpPavementNetwork() {
		NetworkBuilder<Junction> builder = new NetworkBuilder<Junction>("PAVEMENT_NETWORK", pavementJunctionContext, false);
		builder.setEdgeCreator(new NetworkEdgeCreator<Junction>());
		pavementNetwork = builder.buildNetwork();
		
		GISFunctions.buildGISRoadNetwork(pavementLinkGeography, pavementJunctionContext, pavementJunctionGeography, pavementNetwork);
	}
	
	void setUpPedJunctions() throws Exception {
		setUpProperties();
		pedJPath = testGISDir + IO.getProperty("PavementJunctionsShapefile");
		
		// Initialise test road link geography and context
		pavementJunctionContext = new JunctionContext();
		GeographyParameters<Junction> GeoParams = new GeographyParameters<Junction>();
		pavementJunctionGeography = GeographyFactoryFinder.createGeographyFactory(null).createGeography("pavementJunctionGeography", pavementJunctionContext, GeoParams);
		pavementJunctionGeography.setCRS(GlobalVars.geographyCRSString);
				
		GISFunctions.readShapefile(Junction.class, pedJPath, pavementJunctionGeography, pavementJunctionContext);
		SpatialIndexManager.createIndex(pavementJunctionGeography, Junction.class);
	}
	
	List<RoadLink> planStrategicPath(Coordinate o, Coordinate d, String j1ID, String j2ID){
		
		// Plan the strategic path
		List<RoadLink> sP = new ArrayList<RoadLink>();		
		RoadNetworkRoute rnr = new RoadNetworkRoute(o , d);
		
		// Define my starting and ending junctions to test
		// Need to to this because although the origin and destination were selected above, this was just to initialise RoadNetworkRoute with
		// The route is actually calculated using junctions. 
		List<Junction> currentJunctions = new ArrayList<Junction>();
		List<Junction> destJunctions = new ArrayList<Junction>();
		for(Junction j: roadNetwork.getNodes()) {
			
			// Set the test current junctions 
			if (j.getFID().contentEquals(j1ID)) {
				currentJunctions.add(j);
			}
			
			// Set the test destination junctions
			if (j.getFID().contentEquals(j2ID)) {
				destJunctions.add(j);
			}
		}
		
		Junction[] routeEndpoints = new Junction[2];
		
		// Get shortest Route according to the Route class
		List<RepastEdge<Junction>> shortestRoute = null;
		try {
			shortestRoute = rnr.getShortestRoute(roadNetwork, currentJunctions, destJunctions, routeEndpoints, false);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(int i=0; i< shortestRoute.size(); i++) {
			NetworkEdge<Junction> e = (NetworkEdge<Junction>)shortestRoute.get(i);
			sP.add(e.getRoadLink());
		}
		
		return sP;
	}
	

	
	@Test
	void testGetLinksWithinAngularDistance() throws Exception {
		
		// Load links
		setUpRoadLinks("test_strategic_path1.shp");
		
		List<RoadLink> sP = new ArrayList<RoadLink>();
		roadLinkGeography.getAllObjects().forEach(sP::add);
		//Collections.reverse(sP);
		
		Collections.sort(sP, (rl1, rl2) -> rl1.getFID().compareTo(rl2.getFID()));
		
		// Initialise list to contain the links that are within an angular dstance threshold
		List<RoadLink> linksInRange = new ArrayList<RoadLink>();
		
		linksInRange = PedPathFinder.getLinksWithinAngularDistance(sP, 0.0);
		
		assert linksInRange.size() == 1;
		
		linksInRange = PedPathFinder.getLinksWithinAngularDistance(sP, 10.0);
		
		assert linksInRange.size() == 2;
		
		linksInRange = PedPathFinder.getLinksWithinAngularDistance(sP, 50.0);
		
		assert linksInRange.size() == 5;
		
		linksInRange = PedPathFinder.getLinksWithinAngularDistance(sP, 90.0);
		
		assert linksInRange.size() == 6;
		
		linksInRange = PedPathFinder.getLinksWithinAngularDistance(sP, 250.0);
		
		assert linksInRange.size() == 7;
		
	}
	
	@Test
	void testGetLinksWithinAngularDistance2() throws Exception {
		
		// Load links
		setUpRoadLinks("test_strategic_path2.shp");
		
		List<RoadLink> sP = new ArrayList<RoadLink>();
		roadLinkGeography.getAllObjects().forEach(sP::add);
		//Collections.reverse(sP);
		
		Collections.sort(sP, (rl1, rl2) -> rl1.getFID().compareTo(rl2.getFID()));
		
		// Initialise list to contain the links that are within an angular dstance threshold
		List<RoadLink> linksInRange = new ArrayList<RoadLink>();
		
		linksInRange = PedPathFinder.getLinksWithinAngularDistance(sP, 0.0);
		
		assert linksInRange.size() == 1;
		
		linksInRange = PedPathFinder.getLinksWithinAngularDistance(sP, 90.0);
		
		assert linksInRange.size() == 3;
	}
	
	@Test
	void testGetLinksWithinAngularDistance3() throws Exception {
		// Difference between "test_strategic_path3.shp" and "test_strategic_path2.shp"
		// is that "test_strategic_path3.shp" reverses the order of coords for one of the line strings 
		// compared to the others. This tests that angle is still correctly calculated.
		
		// Load links
		setUpRoadLinks("test_strategic_path3.shp");
		
		List<RoadLink> sP = new ArrayList<RoadLink>();
		roadLinkGeography.getAllObjects().forEach(sP::add);
		//Collections.reverse(sP);
		
		Collections.sort(sP, (rl1, rl2) -> rl1.getFID().compareTo(rl2.getFID()));
		
		// Initialise list to contain the links that are within an angular dstance threshold
		List<RoadLink> linksInRange = new ArrayList<RoadLink>();
		
		linksInRange = PedPathFinder.getLinksWithinAngularDistance(sP, 0.0);
		
		assert linksInRange.size() == 1;
		
		linksInRange = PedPathFinder.getLinksWithinAngularDistance(sP, 90.0);
		
		assert linksInRange.size() == 3;
	}
	
	@Test
	void testPavementJunctions() {
		try {
			setUpPedJunctions();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Get a ped junction and check its attributes are not null
		for (Junction pJ: this.pavementJunctionGeography.getAllObjects()) {
			assert pJ.getp1pID() != null;
			assert pJ.getp1rlID() != null;
			assert pJ.getp2pID() != null;
			assert pJ.getp2rlID() != null;

			assert pJ.getv1pID() != null;
			assert pJ.getv1rlID() != null;
			assert pJ.getv2pID() != null;
			assert pJ.getv2rlID() != null;
		}
	}
	
	@Test
	void testPavementNetwork() {
		
		// Call methods that create the pavement junction geography and the pavement network
		try {
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Check all the junctions but this time via the network nodes.
		// Get a ped junction and check its attributes are not null
		for (Junction pJ: this.pavementNetwork.getNodes()) {
			assert pJ.getp1pID() != null;
			assert pJ.getp1rlID() != null;
			assert pJ.getp2pID() != null;
			assert pJ.getp2rlID() != null;

			assert pJ.getv1pID() != null;
			assert pJ.getv1rlID() != null;
			assert pJ.getv2pID() != null;
			assert pJ.getv2rlID() != null;
		}
		
	}

	/*
	 * T-junction straight over
	 */
	@Test
	void testTacticalHorizonEndJunctions1() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Manually set the two road links to get pavement junctions between
		String rlEndHorzID = "9745D155-3C95-4CCD-BC65-0908D57FA83A_0";
		String rlOutHorzID = "A8675945-DE94-4E22-9905-B0623A326221_0";
		
		RoadLink rlEndHorz = null;
		RoadLink rlOutHorz = null;
		for (RoadLink rl : this.roadLinkGeography.getAllObjects()) {
			if (rl.getPedRLID().contentEquals(rlEndHorzID)) {
				rlEndHorz = rl;
				continue;
			}
			
			if (rl.getPedRLID().contentEquals(rlOutHorzID)) {
				rlOutHorz = rl;
				continue;
			}
		}
		
		List<Junction> tacticalEndJunctions = PedPathFinder.tacticalHorizonEndJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		// Now check the nodes as as expected
		String endJID1 = tacticalEndJunctions.get(0).getFID();
		String endJID2 = tacticalEndJunctions.get(1).getFID();
		
		boolean nodeCheck = false;
		if (endJID1.contentEquals("pave_node_89") & endJID2.contentEquals("pave_node_91")) {
			nodeCheck = true;
		}
		else if (endJID1.contentEquals("pave_node_91") & endJID2.contentEquals("pave_node_89")) {
			nodeCheck = true;
		}
		
		assert nodeCheck == true;
	}
	
	/*
	 * T-junction straight ahead, other direction
	 */
	@Test
	void testTacticalHorizonEndJunctions2() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Manually set the two road links to get pavement junctions between
		String rlEndHorzID = "A8675945-DE94-4E22-9905-B0623A326221_0";
		String rlOutHorzID = "9745D155-3C95-4CCD-BC65-0908D57FA83A_0";		
		
		RoadLink rlEndHorz = null;
		RoadLink rlOutHorz = null;
		for (RoadLink rl : this.roadLinkGeography.getAllObjects()) {
			if (rl.getPedRLID().contentEquals(rlEndHorzID)) {
				rlEndHorz = rl;
				continue;
			}
			
			if (rl.getPedRLID().contentEquals(rlOutHorzID)) {
				rlOutHorz = rl;
				continue;
			}
		}
		
		List<Junction> tacticalEndJunctions = PedPathFinder.tacticalHorizonEndJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		// Now check the nodes as as expected
		String endJID1 = tacticalEndJunctions.get(0).getFID();
		String endJID2 = tacticalEndJunctions.get(1).getFID();
		
		boolean nodeCheck = false;
		if (endJID1.contentEquals("pave_node_90") & endJID2.contentEquals("pave_node_91")) {
			nodeCheck = true;
		}
		else if (endJID1.contentEquals("pave_node_91") & endJID2.contentEquals("pave_node_90")) {
			nodeCheck = true;
		}
		
		assert nodeCheck == true;
	}
	
	/*
	 * 4 way junction straight ahead
	 */
	@Test
	void testTacticalHorizonEndJunctions3() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Manually set the two road links to get pavement junctions between
		String rlEndHorzID = "762DB27A-3B61-4EAA-B63E-6F1B0BD80D98_0";
		String rlOutHorzID = "CF9F0CB7-1387-4C83-9D25-98F63CADBE26_0";		
		
		RoadLink rlEndHorz = null;
		RoadLink rlOutHorz = null;
		for (RoadLink rl : this.roadLinkGeography.getAllObjects()) {
			if (rl.getPedRLID().contentEquals(rlEndHorzID)) {
				rlEndHorz = rl;
				continue;
			}
			
			if (rl.getPedRLID().contentEquals(rlOutHorzID)) {
				rlOutHorz = rl;
				continue;
			}
		}
		
		List<Junction> tacticalEndJunctions = PedPathFinder.tacticalHorizonEndJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		// Now check the nodes as as expected
		String endJID1 = tacticalEndJunctions.get(0).getFID();
		String endJID2 = tacticalEndJunctions.get(1).getFID();
		
		boolean nodeCheck = false;
		if (endJID1.contentEquals("pave_node_119") & endJID2.contentEquals("pave_node_121")) {
			nodeCheck = true;
		}
		else if (endJID1.contentEquals("pave_node_121") & endJID2.contentEquals("pave_node_119")) {
			nodeCheck = true;
		}
		
		assert nodeCheck == true;
	}
	
	/*
	 * 4 way junction left turn
	 */
	@Test
	void testTacticalHorizonEndJunctions4() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Manually set the two road links to get pavement junctions between
		String rlEndHorzID = "762DB27A-3B61-4EAA-B63E-6F1B0BD80D98_0";
		String rlOutHorzID = "1DACEAB0-2BA5-4299-8D86-F854C2FAC565_0";		
		
		RoadLink rlEndHorz = null;
		RoadLink rlOutHorz = null;
		for (RoadLink rl : this.roadLinkGeography.getAllObjects()) {
			if (rl.getPedRLID().contentEquals(rlEndHorzID)) {
				rlEndHorz = rl;
				continue;
			}
			
			if (rl.getPedRLID().contentEquals(rlOutHorzID)) {
				rlOutHorz = rl;
				continue;
			}
		}
		
		List<Junction> tacticalEndJunctions = PedPathFinder.tacticalHorizonEndJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		// Now check the nodes as as expected
		String endJID1 = tacticalEndJunctions.get(0).getFID();
		String endJID2 = tacticalEndJunctions.get(1).getFID();
		
		boolean nodeCheck = false;
		if (endJID1.contentEquals("pave_node_119") & endJID2.contentEquals("pave_node_121")) {
			nodeCheck = true;
		}
		else if (endJID1.contentEquals("pave_node_121") & endJID2.contentEquals("pave_node_119")) {
			nodeCheck = true;
		}
		
		assert nodeCheck == true;
	}
	
	/*
	 * Complex covent garden junction straight ahead
	 */
	@Test
	void testTacticalHorizonEndJunctions5() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Manually set the two road links to get pavement junctions between
		String rlEndHorzID = "BBABD5F1-74AC-4481-91C0-61D4C85ABD77_0";
		String rlOutHorzID = "3868DA68-A5D6-4B90-9E0C-4B117146CCFD_0";
		
		RoadLink rlEndHorz = null;
		RoadLink rlOutHorz = null;
		for (RoadLink rl : this.roadLinkGeography.getAllObjects()) {
			if (rl.getPedRLID().contentEquals(rlEndHorzID)) {
				rlEndHorz = rl;
				continue;
			}
			
			if (rl.getPedRLID().contentEquals(rlOutHorzID)) {
				rlOutHorz = rl;
				continue;
			}
		}
		
		List<Junction> tacticalEndJunctions = PedPathFinder.tacticalHorizonEndJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		// Now check the nodes as as expected
		String endJID1 = tacticalEndJunctions.get(0).getFID();
		String endJID2 = tacticalEndJunctions.get(1).getFID();
		
		boolean nodeCheck = false;
		if (endJID1.contentEquals("pave_node_44") & endJID2.contentEquals("pave_node_45")) {
			nodeCheck = true;
		}
		else if (endJID1.contentEquals("pave_node_45") & endJID2.contentEquals("pave_node_44")) {
			nodeCheck = true;
		}
		
		assert nodeCheck == true;
	}
	
	/*
	 * Complex covent garden junction missing pavement nodes
	 */
	@Test
	void testTacticalHorizonEndJunctions6() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Manually set the two road links to get pavement junctions between
		String rlEndHorzID = "3868DA68-A5D6-4B90-9E0C-4B117146CCFD_0";		
		String rlOutHorzID = "A9B5D6A4-C673-4C4F-8DC4-98FB56A72974_0";
		
		RoadLink rlEndHorz = null;
		RoadLink rlOutHorz = null;
		for (RoadLink rl : this.roadLinkGeography.getAllObjects()) {
			if (rl.getPedRLID().contentEquals(rlEndHorzID)) {
				rlEndHorz = rl;
				continue;
			}
			
			if (rl.getPedRLID().contentEquals(rlOutHorzID)) {
				rlOutHorz = rl;
				continue;
			}
		}
		
		List<Junction> tacticalEndJunctions = PedPathFinder.tacticalHorizonEndJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		assert tacticalEndJunctions.size() > 0;
	}
	
	
	/*
	 * 4 way junction straight ahead
	 */
	@Test
	void testTacticalHorizonOutsideJunctions1() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Manually set the two road links to get pavement junctions between
		String rlEndHorzID = "762DB27A-3B61-4EAA-B63E-6F1B0BD80D98_0";
		String rlOutHorzID = "CF9F0CB7-1387-4C83-9D25-98F63CADBE26_0";		
		
		RoadLink rlEndHorz = null;
		RoadLink rlOutHorz = null;
		for (RoadLink rl : this.roadLinkGeography.getAllObjects()) {
			if (rl.getPedRLID().contentEquals(rlEndHorzID)) {
				rlEndHorz = rl;
				continue;
			}
			
			if (rl.getPedRLID().contentEquals(rlOutHorzID)) {
				rlOutHorz = rl;
				continue;
			}
		}
		
		List<Junction> tacticalEndJunctions = PedPathFinder.tacticalHorizonOutsideJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		// Now check the nodes as as expected
		String endJID1 = tacticalEndJunctions.get(0).getFID();
		String endJID2 = tacticalEndJunctions.get(1).getFID();
		
		boolean nodeCheck = false;
		if (endJID1.contentEquals("pave_node_122") & endJID2.contentEquals("pave_node_120")) {
			nodeCheck = true;
		}
		else if (endJID1.contentEquals("pave_node_120") & endJID2.contentEquals("pave_node_122")) {
			nodeCheck = true;
		}
		
		assert nodeCheck == true;
	}
	
	/*
	 * 4 way junction left turn
	 */
	@Test
	void testTacticalHorizonOutsideJunctions2() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Manually set the two road links to get pavement junctions between
		String rlEndHorzID = "762DB27A-3B61-4EAA-B63E-6F1B0BD80D98_0";
		String rlOutHorzID = "1DACEAB0-2BA5-4299-8D86-F854C2FAC565_0";		
		
		RoadLink rlEndHorz = null;
		RoadLink rlOutHorz = null;
		for (RoadLink rl : this.roadLinkGeography.getAllObjects()) {
			if (rl.getPedRLID().contentEquals(rlEndHorzID)) {
				rlEndHorz = rl;
				continue;
			}
			
			if (rl.getPedRLID().contentEquals(rlOutHorzID)) {
				rlOutHorz = rl;
				continue;
			}
		}
		
		List<Junction> tacticalEndJunctions = PedPathFinder.tacticalHorizonOutsideJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		// Now check the nodes as as expected
		String endJID1 = tacticalEndJunctions.get(0).getFID();
		String endJID2 = tacticalEndJunctions.get(1).getFID();
		
		boolean nodeCheck = false;
		if (endJID1.contentEquals("pave_node_122") & endJID2.contentEquals("pave_node_121")) {
			nodeCheck = true;
		}
		else if (endJID1.contentEquals("pave_node_121") & endJID2.contentEquals("pave_node_122")) {
			nodeCheck = true;
		}
		
		assert nodeCheck == true;
	}
	
	/*
	 * Complex covent garden junction straight ahead
	 */
	@Test
	void testTacticalHorizonOutsideJunctions3() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Manually set the two road links to get pavement junctions between
		String rlEndHorzID = "BBABD5F1-74AC-4481-91C0-61D4C85ABD77_0";
		String rlOutHorzID = "3868DA68-A5D6-4B90-9E0C-4B117146CCFD_0";
		
		RoadLink rlEndHorz = null;
		RoadLink rlOutHorz = null;
		for (RoadLink rl : this.roadLinkGeography.getAllObjects()) {
			if (rl.getPedRLID().contentEquals(rlEndHorzID)) {
				rlEndHorz = rl;
				continue;
			}
			
			if (rl.getPedRLID().contentEquals(rlOutHorzID)) {
				rlOutHorz = rl;
				continue;
			}
		}
		
		List<Junction> tacticalEndJunctions = PedPathFinder.tacticalHorizonOutsideJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		
		// Now check the nodes as as expected - only one junction returned in this case due to complicated junction
		String endJID1 = tacticalEndJunctions.get(0).getFID();
		
		boolean nodeCheck = false;
		if (endJID1.contentEquals("pave_node_35")) {
			nodeCheck = true;
		}
		assert nodeCheck == true;
	}
	
	/*
	 * Test the creation of a TacticalRoute object
	 * 
	 * The tactical route provides a path to a pavement junction the end of the pedestrian agents planning horizon and then from that junction
	 * to their destination.
	 * 
	 * This tests whether the expected path is created.
	 */
	@Test
	public void testSetupTacticalRoute1() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Set the IDs of the road network junctions to travel to and get strategic path between these
		String originRoadJunctionID = "node_id_64";
		String destRoadJunctionID = "node_id_73";
		List<RoadLink> sP = planStrategicPath(new Coordinate(), new Coordinate(), originRoadJunctionID, destRoadJunctionID);
		
		
		// Produce strategic path between pavement junctions
		Junction oJ = null;
		Junction dJ = null;
		for (Junction j: this.pavementJunctionGeography.getAllObjects()) {
			if (j.getFID().contentEquals("pave_node_85")) {
				oJ = j;
				continue;
			}
			else if (j.getFID().contentEquals("pave_node_112")) {
				dJ = j;
				continue;
			}
		}
		
		int horizonNLinks = 1;
		RoadLink rlEndHorz = sP.get(horizonNLinks-1);
		RoadLink rlOutHorz = sP.get(horizonNLinks);
		
		// Identify the end and outside junctions
		HashMap<String, List<Junction>> tacticalJunctions = PedPathFinder.tacticalHorizonJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		List<Junction> outsideJunctions = tacticalJunctions.get("outside");
		NetworkPath<Junction> np = new NetworkPath<Junction>(this.pavementNetwork);
		
		// Select which end junction to find tactical path to
		final String end1ID = "pave_node_87";
		Junction endJ = tacticalJunctions.get("end").stream().filter(j -> j.getFID().contentEquals(end1ID)).collect(Collectors.toList()).get(0);
		TacticalAlternative tr = PedPathFinder.setupTacticalAlternativeRoute(np, sP, endJ, outsideJunctions, oJ, dJ);

		// Now validate the tactical route
		// Check expected junctions - should include the starting junction, junctions path passes along and end junction
		String[] expectedJunctions1 = {end1ID};
		List<Junction> rJs =  tr.getRouteJunctions();
		for (int i=0; i<rJs.size(); i++) {
			assert rJs.get(i).getFID().contentEquals(expectedJunctions1[i]);
		}
		
		assert tr.getRoutePath().size() == 1;
		
		// Check remainder path by counting number of times a primary crossing is performed
		List<RepastEdge<Junction>> rP = tr.getAlternativeRemainderPath();
		int count = 0;
		for (RepastEdge<Junction> re: rP) {
			NetworkEdge<Junction> ne = (NetworkEdge<Junction>) re;
			for (RoadLink rl : sP) {
				String rlid = ne.getRoadLink().getPedRLID();
				if (rlid.contentEquals(rl.getPedRLID())){
					count++;
				}
			}
		}
		
		assert (count % 2) == 0; 
		
		
		// Test for other end junction, again should include the starting junction, junctions path passes along and end junction
		final String end2ID = "pave_node_88";
		endJ = tacticalJunctions.get("end").stream().filter(j -> j.getFID().contentEquals(end2ID)).collect(Collectors.toList()).get(0);
		tr = PedPathFinder.setupTacticalAlternativeRoute(np, sP, endJ, outsideJunctions, oJ, dJ);
		String[] expectedJunctions2 =  {end1ID, end2ID};
		rJs =  tr.getRouteJunctions();
		for (int i=0; i<rJs.size(); i++) {
			assert rJs.get(i).getFID().contentEquals(expectedJunctions2[i]);
		}
		
		// Primary crossing required to reach this end junction so expect additional link in path
		assert tr.getRoutePath().size() == 2;
		
		rP = tr.getAlternativeRemainderPath();
		count = 0;
		for (RepastEdge<Junction> re: rP) {
			NetworkEdge<Junction> ne = (NetworkEdge<Junction>) re;
			for (RoadLink rl : sP) {
				if (ne.getRoadLink().getPedRLID().contentEquals(rl.getPedRLID())){
					count++;
				}
			}
		}
		
		assert (count % 2) == 1; 
		
	}
	
	/*
	 * Test the creation of a TacticalRoute object
	 * 
	 * The tactical route provides a path to a pavement junction the end of the pedestrian agents planning horizon and then from that junction
	 * to their destination.
	 * 
	 * This tests tactical route when turning left at 4 way junc
	 */
	@Test
	public void testSetupTacticalRoute2() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Set the IDs of the road network junctions to travel to and get strategic path between these
		String originRoadJunctionID = "node_id_66";
		String destRoadJunctionID = "node_id_73";
		List<RoadLink> sP = planStrategicPath(new Coordinate(), new Coordinate(), originRoadJunctionID, destRoadJunctionID);
		
		
		// Produce strategic path between pavement junctions
		Junction oJ = null;
		Junction dJ = null;
		for (Junction j: this.pavementJunctionGeography.getAllObjects()) {
			if (j.getFID().contentEquals("pave_node_87")) {
				oJ = j;
				continue;
			}
			else if (j.getFID().contentEquals("pave_node_112")) {
				dJ = j;
				continue;
			}
		}
		
		List<RoadLink> tacticalPlanHorz = PedPathFinder.getLinksWithinAngularDistance(sP, 20.00);
		RoadLink rlEndHorz = tacticalPlanHorz.get(tacticalPlanHorz.size()-1);
		RoadLink rlOutHorz = sP.get(tacticalPlanHorz.size());
		
		// Identify the end and outside junctions
		HashMap<String, List<Junction>> tacticalJunctions = PedPathFinder.tacticalHorizonJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		List<Junction> outsideJunctions = tacticalJunctions.get("outside");
		NetworkPath<Junction> p = new NetworkPath<Junction>(this.pavementNetwork);
		
		// Select which end junction to find tactical path to
		final String end1ID = "pave_node_81";
		Junction endJ = tacticalJunctions.get("end").stream().filter(j -> j.getFID().contentEquals(end1ID)).collect(Collectors.toList()).get(0);
		TacticalAlternative tr = PedPathFinder.setupTacticalAlternativeRoute(p, sP, endJ, outsideJunctions, oJ, dJ);
		
		// Now validate the tactical route
		// Check expected junctions - should include the starting junction, junctions path passes along and end junction
		String[] expectedJunctions1 = {end1ID};
		List<Junction> rJs =  tr.getRouteJunctions();
		for (int i=0; i<rJs.size(); i++) {
			assert rJs.get(i).getFID().contentEquals(expectedJunctions1[i]);
		}
		
		assert tr.getRoutePath().size() == 1;
		
		// Check remainder path by counting number of times a primary crossing is performed
		List<RepastEdge<Junction>> rP = tr.getAlternativeRemainderPath();
		int count = 0;
		for (RepastEdge<Junction> re: rP) {
			NetworkEdge<Junction> ne = (NetworkEdge<Junction>) re;
			for (RoadLink rl : sP) {
				String rlid = ne.getRoadLink().getPedRLID();
				if (rlid.contentEquals(rl.getPedRLID())){
					count++;
				}
			}
		}
		
		assert (count % 2) == 0; 
		
		
		// Test for other end junction
		final String end2ID = "pave_node_79";
		endJ = tacticalJunctions.get("end").stream().filter(j -> j.getFID().contentEquals(end2ID)).collect(Collectors.toList()).get(0);
		tr = PedPathFinder.setupTacticalAlternativeRoute(p, sP, endJ, outsideJunctions, oJ, dJ);
		
		// In this case expect that the route goes to end junction and then from end junction to outside junction - the first junction outside the planning horizon - without making a primary crossing.
		String[] expectedJunctions2 =  {"pave_node_88", end2ID, "pave_node_80", "pave_node_82"};
		rJs =  tr.getRouteJunctions();
		for (int i=0; i<expectedJunctions2.length; i++) {
			assert rJs.get(i).getFID().contentEquals(expectedJunctions2[i]);
		}
		
		// Primary crossing required to reach this end junction so expect additional link in path
		assert tr.getRoutePath().size() == 4;
		
		rP = tr.getAlternativeRemainderPath();
		count = 0;
		for (RepastEdge<Junction> re: rP) {
			NetworkEdge<Junction> ne = (NetworkEdge<Junction>) re;
			for (RoadLink rl : sP) {
				if (ne.getRoadLink().getPedRLID().contentEquals(rl.getPedRLID())){
					count++;
				}
			}
		}
		
		assert (count % 2) == 1; 
		
	}
	
	/*
	 * Create two tactical alternatives, on that requires a primary crossing and one that doesn't. This corresponds to a default no cross
	 * tactical alternative and a target crossing tactical alternative.
	 * 
	 * Then update the current junction of the default tactical alternative and use this current junction to update the path of the target crossing tactical alternative.
	 * 
	 *  This replicates the process of a pedestrian agent updating the path of the target tactical alternative to cut out junctions that it has passed while 
	 *  walking along the default route.
	 */
	@Test
	public void testUpdateTacticalAlternative1() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Set the IDs of the road network junctions to travel to and get strategic path between these
		String originRoadJunctionID = "node_id_66";
		String destRoadJunctionID = "node_id_73";
		List<RoadLink> sP = planStrategicPath(new Coordinate(), new Coordinate(), originRoadJunctionID, destRoadJunctionID);
		
		
		// Produce strategic path between pavement junctions
		Junction oJ = null;
		Junction dJ = null;
		for (Junction j: this.pavementJunctionGeography.getAllObjects()) {
			if (j.getFID().contentEquals("pave_node_87")) {
				oJ = j;
				continue;
			}
			else if (j.getFID().contentEquals("pave_node_112")) {
				dJ = j;
				continue;
			}
		}
		
		List<RoadLink> tacticalPlanHorz = PedPathFinder.getLinksWithinAngularDistance(sP, 20.00);
		RoadLink rlEndHorz = tacticalPlanHorz.get(tacticalPlanHorz.size()-1);
		RoadLink rlOutHorz = sP.get(tacticalPlanHorz.size());
		
		// Identify the end and outside junctions
		HashMap<String, List<Junction>> tacticalJunctions = PedPathFinder.tacticalHorizonJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		List<Junction> outsideJunctions = tacticalJunctions.get("outside");
		NetworkPath<Junction> p = new NetworkPath<Junction>(this.pavementNetwork);
		
		// Select which end junction to find tactical path to
		final String defaultEndID = "pave_node_81";
		Junction defaultEndJ = tacticalJunctions.get("end").stream().filter(j -> j.getFID().contentEquals(defaultEndID)).collect(Collectors.toList()).get(0);
		TacticalAlternative trDefault = PedPathFinder.setupTacticalAlternativeRoute(p, sP, defaultEndJ, outsideJunctions, oJ, dJ);
		
		
		// Test for other end junction
		final String targetEndID = "pave_node_79";
		Junction targetEndJ = tacticalJunctions.get("end").stream().filter(j -> j.getFID().contentEquals(targetEndID)).collect(Collectors.toList()).get(0);
		TacticalAlternative trTarget = PedPathFinder.setupTacticalAlternativeRoute(p, sP, targetEndJ, outsideJunctions, oJ, dJ);
		
		// In this case expect that the route goes to end junction and then from end junction to outside junction - the first junction outside the planning horizon - without making a primary crossing.
		String[] expectedInitialTargetRouteJunctions =  {"pave_node_88", targetEndID, "pave_node_80", "pave_node_82"};
		List<Junction> rJs =  trTarget.getRouteJunctions();
		for (int i=0; i<expectedInitialTargetRouteJunctions.length; i++) {
			assert rJs.get(i).getFID().contentEquals(expectedInitialTargetRouteJunctions[i]);
		}
		
		// Now update current junction of default route to emulate pedestrian making progress
		assert trDefault.getCurrentJunction().getFID().contentEquals(defaultEndID);
		
		// Now update the target path so that it corresponds to the situation where the pedestrian's target junction is up to date
		trTarget.updatePathToEnd(trDefault.getCurrentJunction());
		
		String[] expectedUpdateTargetRouteJunctions =  {targetEndID, "pave_node_80", "pave_node_82"};
		rJs =  trTarget.getRouteJunctions();
		for (int i=0; i<expectedUpdateTargetRouteJunctions.length; i++) {
			assert rJs.get(i).getFID().contentEquals(expectedUpdateTargetRouteJunctions[i]);
		}
		
	}
	
	/*
	 *  This test replicates the process of a pedestrian agent updating the path of the target tactical alternative to cut out junctions that it has passed while 
	 *  walking along the default route.
	 *  
	 *  Repeat test for different section of the road network.
	 */
	@Test
	public void testUpdateTacticalAlternative2() {
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Set the IDs of the road network junctions to travel to and get strategic path between these
		String originRoadJunctionID = "node_id_84";
		String destRoadJunctionID = "node_id_76";
		List<RoadLink> sP = planStrategicPath(new Coordinate(), new Coordinate(), originRoadJunctionID, destRoadJunctionID);
		
		
		// Produce strategic path between pavement junctions
		Junction oJ = null;
		Junction dJ = null;
		for (Junction j: this.pavementJunctionGeography.getAllObjects()) {
			if (j.getFID().contentEquals("pave_node_104")) {
				oJ = j;
				continue;
			}
			else if (j.getFID().contentEquals("pave_node_121")) {
				dJ = j;
				continue;
			}
		}
		
		List<RoadLink> tacticalPlanHorz = PedPathFinder.getLinksWithinAngularDistance(sP, 20.00);
		RoadLink rlEndHorz = tacticalPlanHorz.get(tacticalPlanHorz.size()-1);
		RoadLink rlOutHorz = sP.get(tacticalPlanHorz.size());
		
		// Identify the end and outside junctions
		HashMap<String, List<Junction>> tacticalJunctions = PedPathFinder.tacticalHorizonJunctions(pavementNetwork, rlEndHorz, rlOutHorz);
		List<Junction> outsideJunctions = tacticalJunctions.get("outside");
		NetworkPath<Junction> p = new NetworkPath<Junction>(this.pavementNetwork);
		
		// Select which end junction to find tactical path to
		final String defaultEndID = "pave_node_107";
		Junction defaultEndJ = tacticalJunctions.get("end").stream().filter(j -> j.getFID().contentEquals(defaultEndID)).collect(Collectors.toList()).get(0);
		TacticalAlternative trDefault = PedPathFinder.setupTacticalAlternativeRoute(p, sP, defaultEndJ, outsideJunctions, oJ, dJ);
		
		
		// Test for other end junction
		final String targetEndID = "pave_node_106";
		Junction targetEndJ = tacticalJunctions.get("end").stream().filter(j -> j.getFID().contentEquals(targetEndID)).collect(Collectors.toList()).get(0);
		TacticalAlternative trTarget = PedPathFinder.setupTacticalAlternativeRoute(p, sP, targetEndJ, outsideJunctions, oJ, dJ);
		
		// In this case expect that the route goes to end junction and then from end junction to outside junction - the first junction outside the planning horizon - without making a primary crossing.
		String[] expectedInitialTargetRouteJunctions =  {"pave_node_105", targetEndID};
		List<Junction> rJs =  trTarget.getRouteJunctions();
		for (int i=0; i<expectedInitialTargetRouteJunctions.length; i++) {
			assert rJs.get(i).getFID().contentEquals(expectedInitialTargetRouteJunctions[i]);
		}
		
		// Check current junction of default route is as expected
		assert trDefault.getCurrentJunction().getFID().contentEquals("pave_node_103");
		
		// Now update the target path so that it corresponds to the situation where the pedestrian's target junction is up to date
		trTarget.updatePathToEnd(trDefault.getCurrentJunction());
		
		String[] expectedUpdateTargetRouteJunctions =  {"pave_node_105", targetEndID};
		rJs =  trTarget.getRouteJunctions();
		for (int i=0; i<expectedUpdateTargetRouteJunctions.length; i++) {
			assert rJs.get(i).getFID().contentEquals(expectedUpdateTargetRouteJunctions[i]);
		}
		
		// update default route
		trDefault.updateCurrentJunction();
		assert trDefault.getCurrentJunction().getFID().contentEquals(defaultEndID);
		
		// Now update the target path so that it corresponds to the situation where the pedestrian's target junction is up to date
		trTarget.updatePathToEnd(trDefault.getCurrentJunction());
		
		String[] expectedUpdateTargetRouteJunctions2 =  {targetEndID};
		rJs =  trTarget.getRouteJunctions();
		for (int i=0; i<expectedUpdateTargetRouteJunctions2.length; i++) {
			assert rJs.get(i).getFID().contentEquals(expectedUpdateTargetRouteJunctions2[i]);
		}
	}
	
	@Test
	public void testGetCrossingAlternatives() {
		
		// Setup environment
		try {
			setUpObjectGeography();
			
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
			setUpRoads();
			
			setUpODs("OD_pedestrian_nodes.shp");
			
			setUpCrossingAlternatives();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// origin and destination don't matter, just choose arbitrarily
		OD o = null;
		OD d = null;
		for (OD i : this.odGeography.getAllObjects()) {
			if (i.getId()==2) {
				o = i;
			}
			else if (i.getId()==4) {
				d = i;
			}
		}
		Ped p = new Ped(geography, this.roadGeography, o, d, 0.5, 1.0, 0.9, 3.0, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		// Select which set of road links to get crossing alternatives for
		List<RoadLink> rls = new ArrayList<RoadLink>();
		String[] roadLinkIDs = {"A8675945-DE94-4E22-9905-B0623A326221_0", "9745D155-3C95-4CCD-BC65-0908D57FA83A_0"};
		for (RoadLink rl: this.roadLinkGeography.getAllObjects()) {
			for (int i=0;i<roadLinkIDs.length; i++) {
				if(rl.getFID().contentEquals(roadLinkIDs[i])) {
					rls.add(rl);
				}
			}
		}
		
		// Get crossing alternatives
		List<CrossingAlternative> cas = PedPathFinder.getCrossingAlternatives(this.caGeography, rls, p, this.roadGeography);
		
		// Validate crossing alternatives by checking types are as expected
		String[] expectedTypes = {"unsignalised", "unsignalised", "unmarked"};
		for (int i=0;i<expectedTypes.length; i++) {
			assert cas.get(i).getType().contentEquals(expectedTypes[i]);
		}
		
		// Repeat with just one road link
		rls = new ArrayList<RoadLink>();
		String[] roadLinkIDs2 = {"A8675945-DE94-4E22-9905-B0623A326221_0"};
		for (RoadLink rl: this.roadLinkGeography.getAllObjects()) {
			for (int i=0;i<roadLinkIDs2.length; i++) {
				if(rl.getFID().contentEquals(roadLinkIDs2[i])) {
					rls.add(rl);
				}
			}
		}
		
		// Get crossing alternatives
		cas = PedPathFinder.getCrossingAlternatives(this.caGeography, rls, p, this.roadGeography);
		
		String[] expectedTypes2 = {"unsignalised", "unmarked"};
		for (int i=0;i<expectedTypes2.length; i++) {
			assert cas.get(i).getType().contentEquals(expectedTypes2[i]);
		}
	}
	
	/*
	 * Test setting up destination tactical alternative route objects. These are the tactical alternatives that are presented to a pedestrian
	 * when they reach the final tactical planning horizon of their journey.
	 */
	@Test
	public void testDestinationTacticalAlternatives1() {
		
		try {
			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
			setUpRoads();
			
			setUpODs("OD_pedestrian_nodes.shp");
			
			setUpCrossingAlternatives();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// origin and destination don't matter, just choose arbitrarily
		OD o = null;
		OD d = null;
		for (OD i : this.odGeography.getAllObjects()) {
			if (i.getId()==3) {
				o = i;
			}
			else if (i.getId()==2) {
				d = i;
			}
		}
		Ped p = new Ped(geography, this.roadGeography, o, d, 0.5, 1.0, 0.9, 3.0, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		int nLinks = p.getPathFinder().getStrategicPath().size();	

		
		Junction currentJ = p.getPathFinder().getStartPavementJunction();
		Junction destJ = p.getPathFinder().getDestPavementJunction();
		
		List<TacticalAlternative> trs = PedPathFinder.destinationTacticalAlternatives(pavementNetwork, p.getPathFinder().getStrategicPath(), nLinks, currentJ, destJ, this.caGeography, this.roadGeography, p);
		
		TacticalAlternative targetTA = trs.get(0);
		TacticalAlternative nonDestTA = trs.get(1);
		
		// Now run checks
		assert nonDestTA.getEndJunction().getFID().contentEquals("pave_node_119");
		assert targetTA.getEndJunction().getFID().contentEquals(destJ.getFID());
		
		assert nonDestTA.getCrossingAlternatives().size() > 0;
		assert targetTA.getCrossingAlternatives().size() == 0;
		
		assert targetTA.getPathEndToOutside().size()==0;
		assert nonDestTA.getPathEndToOutside().size()==0;
		
		assert targetTA.getPathToEnd().size() == 4;
		assert nonDestTA.getPathToEnd().size() == 5;
		
		assert targetTA.getCurrentJunction().getFID().contentEquals("pave_node_91");
		assert nonDestTA.getCurrentJunction().getFID().contentEquals("pave_node_91");		
	}
	
	/*
	 * Testing the initialisation of a PedPathFinder object. O Id = 4 D id = 1.
	 */
	@Test
	public void testPedPathFinder1() {
		
		// Setup environment
		try {
			setUpObjectGeography();

			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
			setUpCrossingAlternatives();
			
			setUpODs("OD_pedestrian_nodes.shp");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Set the IDs of the road network junctions to travel to and get strategic path between these
		OD o = null;
		OD d = null;
		
		for (OD i : this.odGeography.getAllObjects()) {
			if (i.getId() == 4) {
				o = i;
			}
			else if (i.getId() == 1) {
				d = i;
			}
		}
		
		
		// Set up ped path finder
		PedPathFinder ppf = new PedPathFinder(o, d, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		// Check the start and end pavement junctions are as expected
		assert ppf.getStartPavementJunction().getFID().contentEquals("pave_node_85");
		assert ppf.getDestPavementJunction().getFID().contentEquals("pave_node_93");
		
		Ped p = new Ped(geography, this.roadGeography, o, d, 0.5, 1.0, 0.9, 3.0, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		// Now test planning the first tactical path with this ped path finder object
		ppf.planTacticaAccumulatorPath(this.pavementNetwork, this.caGeography, this.roadGeography, p, ppf.getStrategicPath(), ppf.getStartPavementJunction(), ppf.getDestPavementJunction());
		
		// Check the current (default) and target tactical alternatives are as expected
		assert ppf.getTacticalPath().getCurrentTA().getEndJunction().getFID().contentEquals("pave_node_87");
		assert ppf.getTacticalPath().getTargetTA().getEndJunction().getFID().contentEquals("pave_node_87");
	}
	
	/*
	 * Testing the initialisation of a PedPathFinder object. O Id = 2 D id = 1.
	 * 
	 * Test fails due to issues with strategic path finding model.
	 */
	@Test
	public void testPedPathFinder2() {
		
		// Setup environment
		try {
			setUpObjectGeography();

			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
			setUpCrossingAlternatives();
			
			setUpODs("OD_pedestrian_nodes.shp");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Set the IDs of the road network junctions to travel to and get strategic path between these
		OD o = null;
		OD d = null;
		
		for (OD i : this.odGeography.getAllObjects()) {
			if (i.getId() == 2) {
				o = i;
			}
			else if (i.getId() == 1) {
				d = i;
			}
		}
		
		
		// Set up ped path finder
		PedPathFinder ppf = new PedPathFinder(o, d, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		// Check the strategic path is as expected
		String[] expectedRoadIDs = {"762DB27A-3B61-4EAA-B63E-6F1B0BD80D98_0", "56CF7BBA-28E4-4ACA-9F58-E096E88094FB_0", "B2B9D137-2BA4-4864-8350-2EDAA5910747_0"};
		for (int i=0;i<ppf.getStrategicPath().size(); i++) {
			assert ppf.getStrategicPath().get(i).getFID().contentEquals(expectedRoadIDs[i]);
		}
		
		// Check the start and end pavement junctions are as expected
		assert ppf.getStartPavementJunction().getFID().contentEquals("pave_node_121");
		assert ppf.getDestPavementJunction().getFID().contentEquals("pave_node_80");
		
		Ped p = new Ped(geography, this.roadGeography, o, d, 0.5, 1.0, 0.9, 3.0, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		// Now test planning the first tactical path with this ped path finder object
		ppf.planTacticaAccumulatorPath(this.pavementNetwork, this.caGeography, this.roadGeography, p, ppf.getStrategicPath(), ppf.getStartPavementJunction(), ppf.getDestPavementJunction());
		
		// Check the current (default) and target tactical alternatives are as expected
		assert ppf.getTacticalPath().getCurrentTA().getEndJunction().getFID().contentEquals("pave_node_114");
		assert ppf.getTacticalPath().getTargetTA().getEndJunction().getFID().contentEquals("pave_node_114");
	}
	
	/*
	 * Testing the initialisation of a PedPathFinder object. O Id = 2 D id = 5.
	 * 
	 * Also tests updating the tactical path from a junction part way along the journey.
	 * 
	 */
	@Test
	public void testPedPathFinder3() {
		
		// Setup environment
		try {
			setUpObjectGeography();

			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
			setUpCrossingAlternatives();
			
			setUpODs("OD_pedestrian_nodes.shp");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Set the IDs of the road network junctions to travel to and get strategic path between these
		OD o = null;
		OD d = null;
		
		for (OD i : this.odGeography.getAllObjects()) {
			if (i.getId() == 2) {
				o = i;
			}
			else if (i.getId() == 5) {
				d = i;
			}
		}
		
		
		// Set up ped path finder
		PedPathFinder ppf = new PedPathFinder(o, d, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		// Check the strategic path is as expected
		String[] expectedRoadIDs = {"762DB27A-3B61-4EAA-B63E-6F1B0BD80D98_0", "A8675945-DE94-4E22-9905-B0623A326221_0", "F4C0B1FB-762C-4492-BB0D-673CC4950CBE_0", "8A9E2D7B-3B48-4A19-B89A-0B4F4D516870_2"};
		for (int i=0;i<ppf.getStrategicPath().size(); i++) {
			assert ppf.getStrategicPath().get(i).getFID().contentEquals(expectedRoadIDs[i]);
		}
		
		// Check the start and end pavement junctions are as expected
		assert ppf.getStartPavementJunction().getFID().contentEquals("pave_node_121");
		assert ppf.getDestPavementJunction().getFID().contentEquals("pave_node_87");
		
		Ped p = new Ped(geography, this.roadGeography, o, d, 0.5, 1.0, 0.9, 3.0, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		// Now test planning the first tactical path with this ped path finder object
		ppf.planTacticaAccumulatorPath(this.pavementNetwork, this.caGeography, this.roadGeography, p, ppf.getStrategicPath(), ppf.getStartPavementJunction(), ppf.getDestPavementJunction());
		
		// Check the current (default) and target tactical alternatives are as expected
		assert ppf.getTacticalPath().getCurrentTA().getEndJunction().getFID().contentEquals("pave_node_91");
		assert ppf.getTacticalPath().getTargetTA().getEndJunction().getFID().contentEquals("pave_node_91");
		
		// Test planning the second tactical path
		String[] expectedRouteJunctions = {"pave_node_114", "pave_node_112", "pave_node_91", "pave_node_89"};
		for (int i=0;i<expectedRouteJunctions.length;i++) {
			assert ppf.getTacticalPath().getTargetTA().getRouteJunctions().get(i).getFID().contentEquals(expectedRouteJunctions[i]);
		}
		
		Junction secondStart = ppf.getTacticalPath().getTargetTA().getOutsideJunction();
		List<RoadLink> updatedSP = ppf.getStrategicPath().subList(2, ppf.getStrategicPath().size());
		ppf.planTacticaAccumulatorPath(this.pavementNetwork, this.caGeography, this.roadGeography, p, updatedSP, secondStart, ppf.getDestPavementJunction());
		assert ppf.getTacticalPath().getCurrentTA().getEndJunction().getFID().contentEquals("pave_node_81");
		
	}
	
	/*
	 * Testing the initialisation of a PedPathFinder object. O Id = 3 D id = 5.
	 * 
	 * Tests updating the tactical path until the end of the journey.
	 * 
	 */
	@Test
	public void testPedPathFinder4() {
		
		// Setup environment
		try {
			setUpObjectGeography();

			setUpRoadLinks("open-roads RoadLink Intersect Within simplify angles.shp");
			setUpRoadNetwork(false);
			
			setUpPedJunctions();
			setUpPavementLinks("pedNetworkLinks.shp");
			setUpPavementNetwork();
			
			setUpCrossingAlternatives();
			
			setUpODs("OD_pedestrian_nodes.shp");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// Set the IDs of the road network junctions to travel to and get strategic path between these
		OD o = null;
		OD d = null;
		
		for (OD i : this.odGeography.getAllObjects()) {
			if (i.getId() == 3) {
				o = i;
			}
			else if (i.getId() == 4) {
				d = i;
			}
		}
		
		
		// Set up ped path finder
		PedPathFinder ppf = new PedPathFinder(o, d, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		// Check the strategic path is as expected
		String[] expectedRoadIDs = {"9745D155-3C95-4CCD-BC65-0908D57FA83A_0", "F4C0B1FB-762C-4492-BB0D-673CC4950CBE_0", "8A9E2D7B-3B48-4A19-B89A-0B4F4D516870_2", "8A9E2D7B-3B48-4A19-B89A-0B4F4D516870_1"};
		for (int i=0;i<ppf.getStrategicPath().size(); i++) {
			assert ppf.getStrategicPath().get(i).getFID().contentEquals(expectedRoadIDs[i]);
		}
		
		// Check the start and end pavement junctions are as expected
		assert ppf.getStartPavementJunction().getFID().contentEquals("pave_node_108");
		assert ppf.getDestPavementJunction().getFID().contentEquals("pave_node_85");
		
		Ped p = new Ped(geography, this.roadGeography, o, d, 0.5, 1.0, 0.9, 3.0, this.roadLinkGeography, this.roadNetwork, this.odGeography, this.pavementJunctionGeography, this.pavementNetwork);
		
		// Now test planning the first tactical path with this ped path finder object
		ppf.planTacticaAccumulatorPath(this.pavementNetwork, this.caGeography, this.roadGeography, p, ppf.getStrategicPath(), ppf.getStartPavementJunction(), ppf.getDestPavementJunction());
		
		// Check the current (default) and target tactical alternatives are as expected
		assert ppf.getTacticalPath().getCurrentTA().getEndJunction().getFID().contentEquals("pave_node_91");
		assert ppf.getTacticalPath().getTargetTA().getEndJunction().getFID().contentEquals("pave_node_91");
		
		// Test planning the second tactical path
		String[] expectedRouteJunctions = {"pave_node_91", "pave_node_90"};
		for (int i=0;i<expectedRouteJunctions.length;i++) {
			assert ppf.getTacticalPath().getTargetTA().getRouteJunctions().get(i).getFID().contentEquals(expectedRouteJunctions[i]);
		}
		
		Junction updatedStart = ppf.getTacticalPath().getTargetTA().getOutsideJunction();
		List<RoadLink> updatedSP = ppf.getStrategicPath().subList(1, ppf.getStrategicPath().size());
		ppf.planTacticaAccumulatorPath(this.pavementNetwork, this.caGeography, this.roadGeography, p, updatedSP, updatedStart, ppf.getDestPavementJunction());
		assert ppf.getTacticalPath().getCurrentTA().getEndJunction().getFID().contentEquals("pave_node_82");
		assert ppf.getTacticalPath().getCurrentTA().getOutsideJunction().getFID().contentEquals("pave_node_79");
		
		// Update again
		updatedStart = ppf.getTacticalPath().getTargetTA().getOutsideJunction();
		updatedSP = updatedSP.subList(1, updatedSP.size());
		ppf.planTacticaAccumulatorPath(this.pavementNetwork, this.caGeography, this.roadGeography, p, updatedSP, updatedStart, ppf.getDestPavementJunction());
		assert ppf.getTacticalPath().getCurrentTA().getEndJunction().getFID().contentEquals("pave_node_88");
		assert ppf.getTacticalPath().getCurrentTA().getOutsideJunction().getFID().contentEquals("pave_node_88");
		
		// Update a final time
		// Should get difference between target and current TA because now on final link
		updatedStart = ppf.getTacticalPath().getTargetTA().getOutsideJunction();
		updatedSP = updatedSP.subList(1, updatedSP.size());
		ppf.planTacticaAccumulatorPath(this.pavementNetwork, this.caGeography, this.roadGeography, p, updatedSP, updatedStart, ppf.getDestPavementJunction());
		assert ppf.getTacticalPath().getCurrentTA().getEndJunction().getFID().contentEquals("pave_node_83");
		assert ppf.getTacticalPath().getTargetTA().getEndJunction().getFID().contentEquals("pave_node_85");
		
		assert ppf.getTacticalPath().getCurrentTA().getOutsideJunction() == null;
		assert ppf.getTacticalPath().getTargetTA().getOutsideJunction() == null;
		
		// Finally test updating the targetTA as if it were chosen
		
		// First check the current junction
		assert ppf.getTacticalPath().getCurrentTA().getCurrentJunction().getFID().contentEquals("pave_node_83");
		
		// Then update the target TA
		ppf.getTacticalPath().getTargetTA().updatePathToEnd(ppf.getTacticalPath().getCurrentTA().getCurrentJunction());
		assert ppf.getTacticalPath().getTargetTA().getCurrentJunction().getFID().contentEquals("pave_node_85");
		
	}
}
