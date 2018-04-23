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
import java.util.Random;

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
	UnitType heavyType;
	
	UnitType baseType;
	UnitType barracksType;
	
	static int workerLimit = 6;
	static int fightingUnitsBeforeAttack = 4;
	static int resourceWorkerAmount = 0;
	static int attackDistance = 3;
	static int distanceFromBase = 2;
	static int resourceCheckDistance = 6;
	
	boolean readyForAttack = false;
	boolean builtBarracks = false;
	boolean troopTrainTypeToggle = true;
	
	Random r = new Random();
	
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
             heavyType = utt.getUnitType("Heavy");
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
        	int fightingUnits = 0;
        	int enemyWorkers = 0;
        	
        	// Checks how many resources are on map

            resourceAmount = checkNearResources(p, pgs);
            resourceWorkerAmount = resourceAmount;

            if (resourceWorkerAmount > 2) resourceWorkerAmount = 2;
        	
            // check enemy workers
    		for(Unit unit:pgs.getUnits()) {
                if  (unit.getType().canHarvest && unit.getPlayer()>=0 && unit.getPlayer()!=p.getID()) { 
                	enemyWorkers ++;
                }
    		}
    		workerLimit = enemyWorkers + 1;
    		if (workerLimit >= 7) workerLimit = 7;
    		   
        	// barracks
            for (Unit unit : pgs.getUnits()) {
                if (unit.getType() == barracksType
                        && unit.getPlayer() == player
                        && gs.getActionAssignment(unit) == null) {
                    barracksBehaviour(unit, p, pgs);
                    builtBarracks = true;
                }
            }
            // Ranged units
           for(Unit unit : pgs.getUnits()) {
        	   if (unit.getType()==rangedType && 
		                unit.getPlayer() == player) {
        	   			fightingUnits += 1;
        	   }
           }
           
           if (fightingUnits >= fightingUnitsBeforeAttack) readyForAttack = true;
           else readyForAttack = false;
           
           for(Unit unit : pgs.getUnits()) {
        	   if ((unit.getType()==rangedType || unit.getType()==heavyType) && 
		                unit.getPlayer() == player && 
		                gs.getActionAssignment(unit)==null) {
        		   		fightingUnitBehaviour(unit,p,gs);
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
    	  
    	 // assigns resource workers
    	 for(Unit unit:resourceWorkers) {
        	 // assigns build worker
    		 if (p.getResources() >= barracksType.cost + 2 && builtBarracks == false) {
    		 	buildIfNotAlreadyBuilding(unit, barracksType, unit.getX(), unit.getY(), reservedPositions, p, pgs);
        	 }
    		 else {
        		 workerHarvest(unit, p, pgs);
    		 }
    	 }
    	 
    	
    	 // Tells free workers to attack
    	 for (Unit unit:freeWorkers) {
    		 fightingUnitBehaviour(unit, p, gs);
    	 }
	}

	public void fightingUnitBehaviour(Unit unit, Player p, GameState gs) {
		  PhysicalGameState pgs = gs.getPhysicalGameState();
	        Unit closestEnemy = null;
	        int closestDistance = 0;
	        for(Unit enemyUnit:pgs.getUnits()) {
	            if (enemyUnit.getPlayer()>=0 && enemyUnit.getPlayer()!=p.getID() && enemyUnit.getType() != baseType) { 
	                int d = Math.abs(enemyUnit.getX() - unit.getX()) + Math.abs(enemyUnit.getY() - unit.getY());
	                if (closestEnemy==null || d<closestDistance) {
	                    closestEnemy = enemyUnit;
	                    closestDistance = d;
	                }
	            }
	        }
	        if (closestEnemy==null) {
	        	 for(Unit enemyBase:pgs.getUnits()) {
	 	            if (enemyBase.getPlayer()>=0 && enemyBase.getPlayer()!=p.getID() && enemyBase.getType() == baseType) {
	 	            	closestEnemy = enemyBase;
	 	            }
	        }
	        }
	        // attack enemy when close
	        if (closestDistance <= attackDistance || resourceWorkerAmount == 0 || readyForAttack) {
		        if (closestEnemy!=null) {
		            attack(unit,closestEnemy);
	        }
	        }
	        else {
	        	// Moves away from base
	        	int xSpot = 0;
	        	int ySpot = 0;
	        	for (Unit baseUnit:pgs.getUnits()) {
	        		if (baseUnit.getType()==baseType && 
	        				baseUnit.getPlayer() == p.getID()) {
	        			if ((closestEnemy.getX() - unit.getX()) != 0) xSpot = baseUnit.getX() + distanceFromBase * ((closestEnemy.getX() - unit.getX()) / Math.abs(closestEnemy.getX() - unit.getX()));
	        			if ((closestEnemy.getY() - unit.getY()) != 0) ySpot = baseUnit.getY() + distanceFromBase * ((closestEnemy.getY() - unit.getY()) / Math.abs(closestEnemy.getY() - unit.getY()));			
	        		}
	        	}
	        	
	        	// Random movement so they don't get stuck
	        		
	        	if (r.nextBoolean()) {
	        		if (r.nextBoolean()) xSpot += 1;
	        		else xSpot -= 1;
	        	}
	        	if (r.nextBoolean()) {
	        		if (r.nextBoolean()) ySpot += 1;
	        		else ySpot -= 1;
	        	}
	        	move(unit,xSpot, ySpot);
	        }
	}
    
	public void baseBehaviour(Unit u, Player p, PhysicalGameState pgs) {

		if (builtBarracks == false) {
    	if (p.getResources()>=workerType.cost) train(u, workerType);
		}
	}
	
	public void barracksBehaviour(Unit u, Player p, PhysicalGameState pgs) {
        
        if (troopTrainTypeToggle) {
			if (p.getResources() >= rangedType.cost) {
	            train(u, rangedType);
	            troopTrainTypeToggle = false;
	        }
        }
        else {
        	if (p.getResources() >= heavyType.cost) {
                train(u, heavyType);
                troopTrainTypeToggle = true;
        	}
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
        if (closestDistance > 4) resourceWorkerAmount = 0;
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
	
	public int checkNearResources(Player p, PhysicalGameState pgs) {
        int closeToBase = 0;
        for(Unit baseUnit:pgs.getUnits()) {
            if (baseUnit.getType()==baseType && 
    				baseUnit.getPlayer() == p.getID()) { 
		    	for(Unit resource : pgs.getUnits()) {
		    		if (resource.getType().isResource) {
		    			int d = Math.abs(baseUnit.getX() - resource.getX()) + Math.abs(baseUnit.getY() - resource.getY());
		    			if (d<resourceCheckDistance) {
		    				closeToBase += 1;
		    			}
		            }
	            }
    		}
		 }
		return closeToBase;
	}
	
	@Override
    public List<ParameterSpecification> getParameters()     {
        return new ArrayList<>();
    }
    
}
