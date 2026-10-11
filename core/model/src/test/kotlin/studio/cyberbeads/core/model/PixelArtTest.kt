package studio.cyberbeads.core.model

import org.junit.Assert.*
import org.junit.Test

class PixelArtTest {
 @Test fun brushInterpolatesAndFloodFillDoesNotCrossBoundary(){val blank=List(25){0};val line=PixelOps.stroke(blank,5,2,22,0xff111111.toInt());assertEquals(5,line.count{it!=0});val filled=PixelOps.fill(line,5,0,0xffeeaabb.toInt());assertEquals(10,filled.count{it==0xffeeaabb.toInt()});assertEquals(10,filled.count{it==0});assertEquals(blank,PixelOps.stroke(blank,5,-1,0,1))}
 @Test fun paletteImportsGplAndJsonAndRejectsBadData(){assertEquals(listOf(0xffeeaabb.toInt(),-1),PixelOps.importPalette("[\"#EEAABB\",\"#FFFFFF\",\"#EEAABB\"]"));assertEquals(listOf(0xff0a141e.toInt()),PixelOps.importPalette("GIMP Palette\n10 20 30 name"));assertTrue(runCatching{PixelOps.importPalette("no colors")}.isFailure);assertTrue(runCatching{PixelOps.importPalette((0..512).joinToString(" "){"#%06x".format(it)})}.isFailure)}
 @Test fun frameTimingAndTriggerFlagsAreIndependent(){val a=PixelFrame(listOf(-1),80);val b=PixelFrame(listOf(0),240);val clip=MotionClip("test","test","wave",1,1,listOf(a,b),false,true);assertEquals(a,clip.frameAt(79));assertEquals(b,clip.frameAt(80));assertEquals(a,clip.frameAt(320));assertEquals(b,clip.frameAt(999,false));assertTrue(MotionDefaults.eligible(listOf(clip),"wave",false).isEmpty());assertEquals(listOf(clip),MotionDefaults.eligible(listOf(clip),null,true));assertTrue(MotionDefaults.eligible(listOf(clip.copy(action="sleep")),null,true).isEmpty())}
 @Test fun everyInteractionHasAnEditableDefaultAndFramesAreOpaque(){val art=Artwork("builtin-dog","dog",0,BuiltinPets.pattern("builtin-dog"));assertEquals(6,MotionDefaults.actions.size);MotionDefaults.actions.keys.forEach{action->val clip=MotionDefaults.clip(art,action);assertTrue(clip.frames.size>1);assertTrue(clip.frames.all{f->f.pixels.any{it!=0}&&f.pixels.all{it ushr 24 in listOf(0,255)}})}}
 @Test fun mirrorAndShiftPreserveExpectedCoordinates(){val p=listOf(1,2,3,4,5,6);assertEquals(listOf(3,2,1,6,5,4),PixelOps.mirror(p,3));assertEquals(listOf(0,1,2,0,4,5),PixelOps.shift(p,3,1,0));val pattern=PixelOps.toPattern(listOf(0,-1,0,0),2,2);assertEquals(2,pattern.width);assertEquals(listOf(-1,0,-1,-1),pattern.cells)}
}
