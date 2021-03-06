package repastInterSim.pathfinding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.collections15.Transformer;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.Graph;
import repast.simphony.context.space.graph.ContextJungNetwork;
import repast.simphony.space.graph.JungEdgeTransformer;
import repast.simphony.space.graph.JungNetwork;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.space.projection.ProjectionEvent;
import repast.simphony.space.projection.ProjectionListener;

/*
 * An amended version of the Repast Simphony ShortestPath class that allows bespoke transformers to be used in the shortest path calculation
 */
public class NetworkPath<T> implements ProjectionListener<T> {
	
		private Network<T> net;
		private boolean calc = true;
		private Transformer<RepastEdge<T>,Double> transformer;
		private DijkstraShortestPath<T,RepastEdge<T>> dsp;
	  
		private Stack<T> nodePath;
		private List<Stack<RepastEdge<T>>> edgePaths;
	  
	  /**
	   * Constructor
	   * 
	   * @param net the Network
	   */
	  
	    public NetworkPath(){
	    	
	    }
		public NetworkPath(Network<T> net){
			init(net);
		}
		
		private void init(Network<T> net){
			this.net = net;
			resetConnectionPaths();
			this.setDefaultTransformer();
			this.net.addProjectionListener(this);
		}
		
		/*
		 * Initialise the connection path objects used to record the paths between nodes in the network
		 */
		public void resetConnectionPaths() {
			nodePath = new Stack<T>();
			edgePaths = new ArrayList<Stack<RepastEdge<T>>>();
		}

		/**
		 * Returns a list of RepastEdges in the shortest path from source to target.
		 * 
		 * @param source
		 * @param target
		 * @return
		 */
		public List<RepastEdge<T>> getShortestPath(T source, T target){
			
			if (calc){
				calcShortestPaths();
				calc = false;
			}
			return dsp.getPath(source, target); 
		}
		
		/**
		 * Gets the path length from the source node to the target node.
		 * 
		 * @param source the node we want to get the path length from
		 * @param target the node we want to get the path length to
		 * @return the path length from the source node to the target node.
		 */
		public double getShortestPathLength(T source, T target){
			if (calc){
				calcShortestPaths();
				calc = false;
			}
			
			Number n = dsp.getDistance(source, target);

			if (n != null)
				return n.doubleValue();
			else
				return Double.POSITIVE_INFINITY;
		}
		
		
		/*
		 * Returns a list of all paths of nodes connecting two nodes in the network. Paths containing duplicated nodes are excluded.
		 * 
		 * @param T node
		 * 		The starting node of the path
		 * @param T targetNode
		 * 		The end node of the path
		 * @param Predicate<? super T> nodeFilter
		 * 		Used to filter which nodes can be including in the paths
		 * 
		 * @return List<Stack<T>>
		 * 		The paths
		 */
		public List<Stack<RepastEdge<T>>> getSimplePaths(T node, T targetNode, Predicate<? super T> nodeFilter){
			calcSimplePaths(node, targetNode, nodeFilter);
			List<Stack<RepastEdge<T>>> output = this.edgePaths;
			resetConnectionPaths(); // Empty the paths
			return output;
		}
		
		/*
		 * Returns a list of all paths of nodes connecting two nodes in the network. Paths containing duplicated nodes are excluded.
		 * 
		 * @param T node
		 * 		The starting node of the path
		 * @param T targetNode
		 * 		The end node of the path
		 * 
		 * @return List<Stack<T>>
		 * 		The paths
		 */
		public List<Stack<RepastEdge<T>>> getSimplePaths(T node, T targetNode){
			return getSimplePaths(node, targetNode, null);
		}
		
		/*
		 * Given a path of nodes return the corresponding path of edges that connect these nodes
		 */
		public List<RepastEdge<T>> edgePathFromNodes(Stack<T> nodePath) {
			List<RepastEdge<T>> edgePath = new ArrayList<RepastEdge<T>>();
			for (int i = 0; i<nodePath.size()-1;i++) {
				edgePath.add(net.getEdge(nodePath.get(i), nodePath.get(i+1)));
			}
			return edgePath;
		}
		
		/*
		 * Extract the series of nodes that make up a path of edges
		 */
		public LinkedList<T> nodePathFromEdges(List<RepastEdge<T>> edgePath, T startNode) {
			LinkedList<T> pathNodes = new LinkedList<T>();
			T prev = startNode;
			pathNodes.add(prev);
			for (RepastEdge<T> e: edgePath) {
				if (e.getSource().equals(prev)) {
					prev = e.getTarget();
				}
				else {
					prev = e.getSource();
				}
				pathNodes.add(prev);
			}
			return pathNodes;
		}
		
		/*
		 * Given a list of network edges return the length of this path using the input transformer
		 */
		public static <T> double getPathLength(List<RepastEdge<T>> edgePath, Transformer<RepastEdge<T>,Double> t) {
			double length = 0;
			for (RepastEdge<T> e: edgePath) {
				length += t.transform(e);
			}
			return length;
		}
		
		/*
		 * Given a list of network edges return the length of this path using the class transformer
		 */
		public double getPathLength(List<RepastEdge<T>> edgePath) {
			return getPathLength(edgePath, this.transformer);
		}
		
		/**
		 * Creates shortest path info  nodes using the Jung Dijkstra algorithm
		 */
		private void calcShortestPaths(){
			Graph<T, RepastEdge<T>> graph = null;
			
			if (net instanceof JungNetwork)
				graph = ((JungNetwork<T>)net).getGraph();
			else if (net instanceof ContextJungNetwork)
				graph = ((ContextJungNetwork<T>)net).getGraph();
			
			dsp = new DijkstraShortestPath<T,RepastEdge<T>>(graph, transformer);
		}
		
		/*
		 * Calculate the paths between the node and targetNode that don't contain any duplicate nodes.
		 * 
		 * Importantly, if the start node and targetNode are the same then this function only returns
		 * a single path containing just the start node
		 * 
		 * @param T node
		 * 		The starting node of the path
		 * @param T targetNode
		 * 		The end node of the path
		 * @param Predicate<? super T> nodeFilter
		 * 		Used to filter which nodes can be including in the paths
		 */
		private void calcSimplePaths(T node, T targetNode, Predicate<? super T> nodeFilter) {
	        // First add the node to the path
			nodePath.push(node);
			
			// If target node reached log path and exit
			// Could create edge path here...?
			if (node.equals(targetNode)) {
	           Stack<RepastEdge<T>> temp = new Stack<RepastEdge<T>>();
	           for (int i = 0; i<nodePath.size()-1;i++) {
	        	   T ni = nodePath.get(i);
	        	   T nj = nodePath.get(i+1);
	        	   RepastEdge<T> e = net.getEdge(ni, nj);
	               temp.add(e);
	           }
	           edgePaths.add(temp);
			}
			
			// Otherwise get children nodes and add them to the path
			else {
				// Then find valid children nodes
				LinkedHashSet<T> adj = (LinkedHashSet<T>)net.getAdjacent(node);
				Iterable<T> validAdj;
				if (nodeFilter == null) {
					validAdj = adj;
				}
				else {
					validAdj = adj.stream().filter(nodeFilter).collect(Collectors.toList());
				}
				
			    for (T nextNode : validAdj) {
			    	if (!nodePath.contains(nextNode)) {
			           calcSimplePaths(nextNode, targetNode, nodeFilter);
			           nodePath.pop(); // Clears connection path on the way out
				    }
				}
			}
		}
		
		
		/*
		 * Set the transformer
		 */
		public void setTransformer(Transformer<RepastEdge<T>,Double> t) {
			this.transformer = t;
			this.calc = true;
		}
		
		/*
		 * Set the transformer to be the default transformer the class is initialised with
		 */
		public void setDefaultTransformer() {
			this.transformer = new JungEdgeTransformer<T>();
			this.calc = true;
		}
		
		/**
		 * Called when the network is modified so that this will recalculate the
		 * shortest path info.
		 * 
		 * @param evt
		 */
		public void projectionEventOccurred(ProjectionEvent<T> evt) {
			if (evt.getType() != ProjectionEvent.OBJECT_MOVED) {
				calc = true;
			}
		}
		
		public Network<T> getNet() {
			return net;
		}

		/**
		 * Removes this as a projection listener when this ShortestPath is garbage
		 * collected.
		 */
		public void finalize() {
			if (net != null)
				net.removeProjectionListener(this);
		}
		
		/**
		 * Null the object so that the Garbage Collector recognizes to remove 
		 * the object from the jvm.
		 */
		public static NetworkPath<?> finished(NetworkPath<?> sp){
			sp.finalize();
			sp=null;
			return sp;
		}		
}
