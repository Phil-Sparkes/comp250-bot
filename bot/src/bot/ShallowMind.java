/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bot;

import ai.abstraction.AbstractAction;
import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.Harvest;
import ai.abstraction.pathfinding.GreedyPathFinding;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;

import ai.core.AI;
import ai.core.ParameterSpecification;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.units.*;

/**
 *
 * @author santi
 */
public class ShallowMind extends AbstractionLayerAI {    
	
	protected UnitTypeTable utt;
	
	UnitType workerType;
	UnitType rangedType;
	
	UnitType baseType;
	UnitType barracksType;
	
	static int workerLimit = 7;
	static int resourceWorkerAmount = 1;
	boolean builtBarracks = false;
	 
    public ShallowMind(UnitTypeTable a_utt) {
    	this(a_utt, new GreedyPathFinding());
    }
    

    public ShallowMind(UnitTypeTable a_utt, PathFinding a_pf) {
    	super(a_pf);
        reset(a_utt);
    }
    
    
    @Override
    public void reset(UnitTypeTable a_utt) {
    	 utt = a_utt;
         if (utt!=null) {
             workerType = utt.getUnitType("Worker");
             baseType = utt.getUnitType("Base");
             barracksType = utt.getUnitType("Barracks");
             rangedType = utt.getUnitType("Ranged");
         }
    }

    
    @Override
    public AI clone() {
        return new ShallowMind(utt, pf);
    }
   
    
    @Override
    public PlayerAction getAction(int player, GameState gs) {
    	
    		PhysicalGameState pgs = gs.getPhysicalGameState();
        	Player p = gs.getPlayer(player);
        	int resourceAmount = 0;
        	
        	// Checks how many resources are on map
        	for(Unit unit : pgs.getUnits()) {
        		 if (unit.getType().isResource) {
                    resourceAmount += 1;
                }
        	}
        	//System.out.println(resourceAmount);
        	
        	if (resourceAmount >= 4) {
        		resourceWorkerAmount = 2;
        	}
    		if (resourceAmount <= 1) {
    			resourceWorkerAmount = 0;
    		}
        	
        	// barracks
            for (Unit unit : pgs.getUnits()) {
                if (unit.getType() == barracksType
                        && unit.getPlayer() == player
                        && gs.getActionAssignment(unit) == null) {
                    barracksBehaviour(unit, p, pgs);
                }
            }
        	
            // attack units
        	for(Unit unit : pgs.getUnits()) {
        		if (unit.getPlayer() == player && unit.getType().canAttack && 
        				!unit.getType().canHarvest
        				&& gs.getActionAssignment(unit) == null) {
        			meleeUnitBehaviour(unit,p,gs);
				}
        	}
            
            List<Unit> workers = new LinkedList<Unit>();
            for(Unit unit:pgs.getUnits()) {
                if (unit.getType().canHarvest && 
                	unit.getPlayer() == player) {
                    workers.add(unit);
                }        
            }
            
            
         // Bases
            if (workers.size() < workerLimit) {
		    	for(Unit unit : pgs.getUnits()) {
		            if (unit.getType()==baseType && 
		                unit.getPlayer() == player && 
		                gs.getActionAssignment(unit)==null) {
		                baseBehaviour(unit,p,pgs);
		            }
		    	}
            }
            workersBehaviour(workers,p,gs);
            
            return translateActions(player,gs);
        }
    
    
    public void workersBehaviour(List<Unit> workers, Player p, GameState gs) {
    	 PhysicalGameState pgs = gs.getPhysicalGameState();
    	 Unit harvestWorker = null;
    	 Unit buildWorker = null;
    	 
    	 List<Unit> freeWorkers = new LinkedList<Unit>();
    	 List<Unit> resourceWorkers = new LinkedList<Unit>();
    	 List<Integer> reservedPositions = new LinkedList<Integer>();
    	 
    	 freeWorkers.addAll(workers);
    	 
    	 if (workers.isEmpty()) return;
    	 
    	 for (int x = 0; x <resourceWorkerAmount; x++) {
	    	 if (resourceWorkers.size() < resourceWorkerAmount) 	    	 {
	    		 if (freeWorkers.size()>0) harvestWorker = (freeWorkers.remove(0));
	    		 	resourceWorkers.add(harvestWorker);
	    	 }
    	 }
    	 
    	 // assigns build worker
    	 if (p.getResources() >= baseType.cost && builtBarracks == false) {
    		 if (resourceWorkers.size()>1) buildWorker = (resourceWorkers.remove(1));
    		 	builtBarracks = true;
    		 	resourceWorkerAmount = resourceWorkerAmount - 1;
    		 	buildIfNotAlreadyBuilding(buildWorker, barracksType, buildWorker.getX(), buildWorker.getY(), reservedPositions, p, pgs);
    		 	System.out.println("building");
    	 }
    	 
    	 // assigns resource workers
    	 for(Unit unit:resourceWorkers) {
    		 workerHarvest(unit, p, pgs);
    	 }
    	 
    	
    	 // Tells free workers to attack
    	 for (Unit unit:freeWorkers) {
    		 meleeUnitBehaviour(unit, p, gs);
    	 }
	}

    
	public void meleeUnitBehaviour(Unit unit, Player p, GameState gs) {
		  PhysicalGameState pgs = gs.getPhysicalGameState();
	        Unit closestEnemy = null;
	        int closestDistance = 0;
	        for(Unit unit2:pgs.getUnits()) {
	            if (unit2.getPlayer()>=0 && unit2.getPlayer()!=p.getID()) { 
	                int d = Math.abs(unit2.getX() - unit.getX()) + Math.abs(unit2.getY() - unit.getY());
	                if (closestEnemy==null || d<closestDistance) {
	                    closestEnemy = unit2;
	                    closestDistance = d;

	                }
	            }
	        }
	        if (closestEnemy!=null) {
	            attack(unit,closestEnemy);
	        }
	}
    

	public void baseBehaviour(Unit u, Player p, PhysicalGameState pgs) {
    	if (p.getResources()>=workerType.cost) train(u, workerType);
	}
	
	public void barracksBehaviour(Unit u, Player p, PhysicalGameState pgs) {
        if (p.getResources() >= rangedType.cost) {
            train(u, rangedType);
        }
    }

	
	public void workerHarvest (Unit unit, Player p, PhysicalGameState pgs) 	{
		Unit closestBase = null;
        Unit closestResource = null;
        int closestDistance = 0;
        for(Unit u2:pgs.getUnits()) {
            if (u2.getType().isResource) { 
                int d = Math.abs(u2.getX() - unit.getX()) + Math.abs(u2.getY() - unit.getY());
                if (closestResource==null || d<closestDistance) {
                    closestResource = u2;
                    closestDistance = d;
                }
            }
        }
        closestDistance = 0;
        for(Unit u2:pgs.getUnits()) {
            if (u2.getType().isStockpile && u2.getPlayer()==p.getID()) { 
                int d = Math.abs(u2.getX() - unit.getX()) + Math.abs(u2.getY() - unit.getY());
                if (closestBase==null || d<closestDistance) {
                    closestBase = u2;
                    closestDistance = d;
                }
            }
        }
        if (closestResource!=null && closestBase!=null) {
            AbstractAction aa = getAbstractAction(unit);
            if (aa instanceof Harvest) {
                Harvest h_aa = (Harvest)aa;
                if (h_aa.getTarget() != closestResource || h_aa.getBase() !=closestBase) harvest(unit, closestResource, closestBase);
            } else {
                harvest(unit, closestResource, closestBase);
            }
        }
	}

	@Override
    public List<ParameterSpecification> getParameters()     {
        return new ArrayList<>();
    }
    
}
