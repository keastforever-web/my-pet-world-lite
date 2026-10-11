package studio.cyberbeads.core.model
import org.junit.Test
import org.junit.Assert.*
class CustomActionsV14Test {
 @Test fun customLabelsAndOrderComeFromStableIds(){val p=List(4){-1};val first=MotionClip("seq-a","第一组","custom-first",2,2,listOf(PixelFrame(p)),actionName="跳舞");val second=first.copy(id="seq-b",action="custom-second",actionName="躺平");assertEquals(listOf("custom-first","custom-second"),MotionDefaults.groups(listOf(first,second,first.copy(id="seq-c"))));assertEquals("跳舞",MotionDefaults.label(first.action,listOf(first,second)));assertEquals(listOf("custom-first","custom-second"),MotionDefaults.groups(listOf(first.copy(random=true),second)))}
 @Test fun unsupportedIdsAreRejected(){assertTrue(MotionDefaults.validAction("custom-550e8400-e29b-41d4-a716-446655440000"));assertFalse(MotionDefaults.validAction("feed"));assertFalse(MotionDefaults.validAction("../../pet"));assertFalse(MotionDefaults.validAction("unknown"))}
 @Test fun worldUnitsHaveEqualPhysicalMeaningOnBothAxes(){assertEquals(WorldPoint(0f,0f).distance(WorldPoint(.03f,0f)),WorldPoint(0f,0f).distance(WorldPoint(0f,.02f)),.001f)}
}
