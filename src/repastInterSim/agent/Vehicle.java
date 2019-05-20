package repastInterSim.agent;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

import repast.simphony.context.Context;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.space.gis.Geography;
import repast.simphony.util.ContextUtils;
import repastInterSim.environment.Route;
import repastInterSim.main.GlobalVars;
import repastInterSim.main.SpaceBuilder;

public class Vehicle implements mobileAgent {

	private int maxSpeed, followDist; // The distance from a vehicle ahead at which the agent adjusts speed to follow
	private double speed;
	private double acc;
	private double bearing;
	private double dmax;	    
	private Geography<Object> geography;
	private Coordinate vLoc; // The coordinate of the centroid of the vehicle agent.
	
	public Coordinate destCoord;
	
	private Route route;


	public Vehicle(Geography<Object> geography, int mS, double a, double s, Coordinate dC) {
		this.geography = geography;
		this.maxSpeed = mS;
		this.acc = a;
		this.speed = s;
		this.dmax = 20/GlobalVars.spaceScale; // Assuming vehicle drivers adjust their driving according to what's happening 20m in front.
		this.destCoord = dC;
		
		this.route = new Route(geography, this, destCoord);
	}

	/*
	 * Default behaviour, shuffle = true, randomised the scheduling of collections
	 * of agents. In the case of a space, the process may not be random since the
	 * vehicle in the front might have priority.
	 */
	@ScheduledMethod(start = 1, interval = 1, shuffle = false)
	public void step() throws Exception {
		
    	// Check that a route has been generated
    	if (this.route.getRouteX() == null) {
    		this.route.setRoute();
		}
    	
		// Check for nearby cars
		Vehicle vehicleInFront = getVehicleInFront(0);

		// Drive
		drive(vehicleInFront);
		// moveForward();
	}
	
	
    // Function to calculate distance to nearest collision for a given angle f(a) -  this will need to account for movements of other peds
    public Vehicle getVehicleInFront(double alpha)  {
    	
    	// Initialise distance to nearest object as the max distance in the field of vision
    	double d = this.dmax;
    	Vehicle vInFront = null;
    	
    	// Get unit vector in the direction of the sampled angle
    	double[] rayVector = {Math.sin(alpha), Math.cos(alpha)};
    	
    	// Get the coordinate of the end of the field of vision in this direction
    	Coordinate rayEnd = new Coordinate(vLoc.x + rayVector[0]*this.dmax, vLoc.y + rayVector[1]*this.dmax);
    	
    	Coordinate[] lineCoords = {vLoc, rayEnd};
    	// Create a line from the pedestrian to the end of the field of vision in this direction
    	LineString sampledRay = new GeometryFactory().createLineString(lineCoords);
    	
    	// Check to see if this line intersects with any pedestrian agents
        Context<Object> context = ContextUtils.getContext(this);
        for (Object agent :context.getObjects(Vehicle.class)) {
        	Vehicle V = (Vehicle)agent;
        	if (V != this) {
               	Geometry agentG = SpaceBuilder.getAgentGeometry(geography, V);
               	if (agentG.intersects(sampledRay)) {
               		// The intersection geometry could be multiple points.
               		// Iterate over them find the distance to the nearest pedestrian
               		Coordinate[] agentIntersectionCoords = agentG.intersection(sampledRay).getCoordinates();
               		for(Coordinate c: agentIntersectionCoords) {
                   		double dAgent = vLoc.distance(c);
                   		if (dAgent < d) {
                   			d = dAgent;
                   			vInFront = V;
                   		}
               		}
               	}
        	}
        }
        
        return vInFront;    	
    }


	/*
	 * Simply sets speed to be the speed of the vehicle in front accounting for the
	 * acceleration or deceleration required to get to that speed in the next
	 * timestep. This assumes a fixed acceleration.
	 * 
	 * @param vehicleInFront Vehicle. The vehicle agent in front of this vehicle
	 * agent
	 * 
	 * @return Double. The speed set for this vehicle
	 */
	public double setSpeedFollowing(Vehicle vehicleInFront) {

		/*
		 * Set speed so that after one time step it will be the same as the car in front
		 * The vehicle in front is not null only if it is within the following distance
		 * of this agent vehicle.
		 */
		if (vehicleInFront != null) {
			// Get speed of vehicle in front
			double vifSpeed = vehicleInFront.getSpeed();
			this.speed = vifSpeed - (this.acc * GlobalVars.stepToTimeRatio);

		}
		// If there is no vehicle in front just speed up
		else {
			this.speed = this.speed + (this.acc * GlobalVars.stepToTimeRatio);
		}

		enforceSpeedLimit();
		return this.speed;
	}

	public double enforceSpeedLimit() {
		// Enforce speed limits
		if (this.speed > this.maxSpeed) {
			this.speed = this.maxSpeed;
		}
		// Min speed is zero (no-reversing)
		if (this.speed < 0) {
			this.speed = 0;
		}
		return this.speed;
	}

	/*
	 * Set the speed and acceleration of the vehicle agent such that it will come to
	 * a complete stop at the signal.
	 * 
	 * Doesn't account for leaving space for other cars.
	 */
	/*
	public double setAccSignal(Signal s, Vehicle vehicleInFront) {
		double d; // initialise the distance the vehicle must stop in
		double sigX = this.geography.getLocation(s).getX();
		double vX = this.geography.getLocation(this).getX();
		
		// How to ensure there is some sort of buffer
		if (vehicleInFront == null) {
			// Follow the signal
			setAccFollowing(s);
		} else {
			double vifX = this.geography.getLocation(vehicleInFront).getX();

			// Depending on whether the vehicle in front or the signal is closer, set the
			// stopping distance
			if (vifX < sigX) {
				//d = vifX - vX - this.buffer;
				// Follow the vehicle in front
				setAccFollowing(vehicleInFront);
			} else {
				//d = sigX - vX - this.buffer;
				setAccFollowing(s);
			}
		}
		
		// Assumes vehicles come to complete stop in a single time step - might be cause of bunching
		//this.acc = - Math.pow(this.speed, 2) / (2 * d); // Get required deceleration using eqns of constant a

		return this.acc;
	}
	*/

	/*
	 * Updates the vehicle's acceleration using the General Motors car following
	 * model described here: {@link
	 * https://nptel.ac.in/courses/105101008/downloads/cete_14.pdf}. This model
	 * might not be suitable for this simple exercise.
	 * 
	 * @param vehicleInFront The vehicle immediately in front of the ego vehicle
	 * 
	 * @return The updated acceleration
	 */
	public double setAccFollowing(Object objectInFront) {
		// Update acceleration based on the position and velocity of the vehicle in
		// front.
		
		double objV = 0;
		if (objectInFront instanceof Vehicle) {
			objV = ((Vehicle) objectInFront).getSpeed();
		}
		
		/*
		else if (objectInFront instanceof Signal) {
			objV = 0;
		}
		*/
	
		// Only do this if there is a vehicle in front to follow
		if (objectInFront != null) {
			int alpha, m, l;
			alpha = 1;
			m = 0;
			l = 0; // Parameters for the car following model. Needs refactor.
			
			Coordinate vifPt = SpaceBuilder.getAgentGeometry(geography, objectInFront).getCentroid().getCoordinate();

			// Acceleration is negative since in order to have caught up to car in front
			// will have been travelling faster
			this.acc = (((alpha * Math.pow(this.speed,m)) / Math.pow(vLoc.distance(vifPt),l)) * (objV - this.speed));
		} else {
			this.acc = GlobalVars.defaultVehicleAcceleration; // Default acceleration
		}

		return this.acc;
	}

	/*
	 * Drive the vehicle agent. Set the vehicle agent's speed and update the
	 * x-coordinate of the vehicle using its current speed and acceleration and
	 * preventing overtaking. Move the vehicle agent to its new location.
	 * 
	 * Prevention of overtaking is not currently working.
	 * 
	 * @param vehicleInFront Vehicle. The vehicle in front of the agent vehicle
	 * 
	 */
	public void drive(Vehicle vehicleInFront) {

		// Check for a traffic signal
		boolean sigState = true;
		double disp = 0; // Initialise the amount to move the vehicle by

		if (sigState == true) {
			// Continue driving by following car ahead
			// Update acceleration. travel for time step at this acceleration, leading to an updated speed
			setAccFollowing(vehicleInFront);
			disp = this.speed * GlobalVars.stepToTimeRatio + 0.5 * this.acc * Math.pow(GlobalVars.stepToTimeRatio, 2);
			setSpeed();

			// setAcc(vehicleInFront);
		} 
		/*
		else if (sigState == false) {
			// Set speed based on distance from signal
			// In this case signal will be within a certain distance of the vehicle
			Signal sig = getSignal();
			setAccSignal(sig, vehicleInFront);
			disp = this.speed * GlobalVars.stepToTimeRatio + 0.5 * this.acc * Math.pow(GlobalVars.stepToTimeRatio, 2);
			setSpeed();
		}
		*/
		
		// get the next coordinate along the route
		Integer i = 0;
		double distanceAlongRoute = 0;
		
		while (disp > distanceAlongRoute) {
			// Get next coordinate along the route
	        Coordinate routeCoord = this.route.getRouteXCoordinate(0);
	        
	        // Calculate the distance to this coordinate
			double[] distAndAngle = new double[2];
			Route.distance(vLoc, routeCoord, distAndAngle);
			
			double distToCoord = distAndAngle[0];
			
			if (distToCoord > disp) {
				SpaceBuilder.moveAgentByVector(geography, this, disp, distAndAngle[1]);
			}
			
			else {
				// Move to the coordinate and repeat with next coordinate along
				SpaceBuilder.moveAgentByVector(geography, this, distToCoord, distAndAngle[1]);
				distanceAlongRoute += distToCoord;
				this.route.removeRouteXCoordinate(routeCoord);
			}
		}
	}



	/*
	 * Get the signal agent in the space continuous space
	 * 
	 * @return Signal. The signal agent
	 */
	/*
	public Signal getSignal() {
		Signal sig = null;
		for (Object o : this.geography.getObjects()) {
			if (o instanceof Signal) {
				sig = (Signal) o;
			}
		}

		return sig;
	}
	*/

	/*
	 * Identify the signal agent in the space and how far away it is. If signal
	 * agent is within a threshold distance get the state of the signal
	 * 
	 * @return Boolean. True if there is no need to adjust driving. False if signal
	 * means stop.
	 */
	/*
	public boolean checkSignal() {
		double threshDist = 5; // Distance at which drivers will alter behaviour depending on signal
		Signal sig = getSignal();

		// First check if vehicle has past the signal, in which case there is no signal
		// to check
		double sigX = this.geography.getLocation(sig).getX();
		double vX = this.geography.getLocation(this).getX();
		if (vX > sigX) {
			return true;
		} else if ((sigX - vX) < threshDist) {
			return sig.getState();
		} else {
			return true;
		}

	}
	*/
	
	/*
	 * Updates the vehicle's speed using the General Motors car following model
	 * described here: {@link
	 * https://nptel.ac.in/courses/105101008/downloads/cete_14.pdf} In future this
	 * will be revised to ensure a good academic car following model is used
	 * 
	 * @param vehcileInFront The vehicle immediately in front of the ego vehicle
	 * 
	 * @return The new speed
	 */
	public double setSpeed() {
		// Update velocity
		this.speed = this.speed + this.acc * GlobalVars.stepToTimeRatio;
		
		enforceSpeedLimit();

		return this.speed;

	}
	
	public double getSpeed() {
		return this.speed;
	}
	
    /*
     * Get the coordinate of the agents centroid in the references frame used 
     * by the agent class for GIS calculations. Note that this attribute is updated
     * as part of the algorithm for producing pedestrian movement.
     * 
     * @returns Coordinate. The coordinate of the centroid of the pedestrian agent.
     */
    public Coordinate getLoc() {
    	return this.vLoc;
    }
    
    /*
     * Set the location attribute of the agent to be the coordinate of its 
     * centroid, in the coordinate reference frame used by the agent for GIS calculations. 
     */
    public void setLoc()  {
    	// Get centroid coordinate of this agent
    	Coordinate vL = SpaceBuilder.getAgentGeometry(geography, this).getCentroid().getCoordinate();
    	this.vLoc = vL;
    }

}