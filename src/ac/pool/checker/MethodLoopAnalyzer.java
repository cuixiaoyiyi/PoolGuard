package ac.pool.checker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

/**
 * The loop summary of method
 * 
 * @author Baoquan Cui
 * @version 1.0
 */



public class MethodLoopAnalyzer {

	protected List<UnitInfo> mUnitInfos = new ArrayList<UnitInfo>();

	protected Set<Unit> mLoopHeaderList = new HashSet<>();

	protected UnitGraph mUnitGraph = null;
	/**
	 *  unit存储实际的Soot语句对象: 可能是赋值语句、方法调用、条件跳转等
	 *  分析的基本单位，每个被分析的语句都有一个对应的UnitInfo.
	 */
	static class UnitInfo {
		protected Unit mUnit = null;         
		protected boolean visited = false;
		protected int mDeepFirstSearchPathPosition = 0;
		protected Unit mLoopHeaderUnit = null;
		protected boolean isCancelled = false;


		



		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof UnitInfo)) {
				return mUnit.equals(obj);
			}
			UnitInfo other = (UnitInfo) obj;
			return mUnit.equals(other.mUnit);
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append("unit-->" + mUnit);
			sb.append("\n");
			sb.append("iloop_header-->" + mLoopHeaderUnit);
			sb.append("\n");
			sb.append("DFEP_pos-->" + mDeepFirstSearchPathPosition);
			return sb.toString();
		}

	}



	public MethodLoopAnalyzer(SootMethod methodUnderAnalysis) {
		if (methodUnderAnalysis.hasActiveBody()) {
			mUnitGraph = new BriefUnitGraph(methodUnderAnalysis.getActiveBody());
		}
	}

	public Set<Unit> getLoopStartUnits() {
		return mLoopHeaderList;
	}









	protected void generation() {
		if (mUnitGraph == null) {
			return;
		}

		
		for (Unit head : mUnitGraph.getHeads()) {
			traverUnitGraph(head, 0);
		}

		for (UnitInfo unitInfo : mUnitInfos) {

			mLoopHeaderList.add(unitInfo.mLoopHeaderUnit);
		}
		afterLoopAnalysis();

	}

	protected void afterLoopAnalysis() {

	}



	private Unit traverUnitGraph(Unit b0, int deepFirstSearchPathPosition) {
		



		UnitInfo unitInfo = getUnitInfo(b0);
		unitInfo.visited = true;
		unitInfo.mDeepFirstSearchPathPosition = deepFirstSearchPathPosition;
		for (Unit b : mUnitGraph.getSuccsOf(b0)) {
			UnitInfo unitInfob = getUnitInfo(b);

			if (!unitInfob.visited) {
				
				Unit nh = traverUnitGraph(b, deepFirstSearchPathPosition + 1);
				tagLoopHeader(b0, nh);
			}
			else
			{
				if (unitInfob.mDeepFirstSearchPathPosition > 0) {
					
					unitInfob.visited = true;
					tagLoopHeader(b0, b);
				}
				else if (unitInfob.mLoopHeaderUnit == null) {
					
				}
				else {
					Unit h = unitInfob.mLoopHeaderUnit;
					UnitInfo unitInfoH = getUnitInfo(h);
					if (unitInfoH.mDeepFirstSearchPathPosition > 0) {
						

						tagLoopHeader(b0, h);
					} else {
						
						while (unitInfoH.mLoopHeaderUnit != null) {
							unitInfoH = getUnitInfo(unitInfoH.mLoopHeaderUnit);
							if (unitInfoH.mDeepFirstSearchPathPosition > 0) {
								tagLoopHeader(b0, h);
								break;
							}
						}
					}
				}
			}
		}
		unitInfo.mDeepFirstSearchPathPosition = 0;
		return unitInfo.mLoopHeaderUnit;
	}



	private void tagLoopHeader(Unit b, Unit h) {


		
		if (b == h || h == null) {  
			return;
		}
		UnitInfo cur1 = getUnitInfo(b);
		UnitInfo cur2 = getUnitInfo(h);

		while (cur1.mLoopHeaderUnit != null) {
			UnitInfo ih = getUnitInfo(cur1.mLoopHeaderUnit);
			if (ih == cur2) {
				return;
			}

			if (ih.mDeepFirstSearchPathPosition < cur2.mDeepFirstSearchPathPosition) {
				cur1.mLoopHeaderUnit = cur2.mUnit;
				cur1 = cur2;
				cur2 = ih;
			} else {
				cur1 = ih;
			}
		}

		cur1.mLoopHeaderUnit = cur2.mUnit;
	}


	protected UnitInfo getUnitInfo(Unit unit) {
		for (UnitInfo unitInfo : mUnitInfos) {
			if (unitInfo.mUnit.equals(unit)) {
				return unitInfo;
			}
		}
		UnitInfo unitInfo = new UnitInfo();
		unitInfo.mUnit = unit;
		mUnitInfos.add(unitInfo);
		return unitInfo;
	}



}
