package studio.cyberbeads.app
import androidx.compose.runtime.Composable
import studio.cyberbeads.core.data.ArtworkStore
@Composable fun editionEntry(onClick:()->Unit):(@Composable ()->Unit)?=null
@Composable fun EditionPark(artworks:ArtworkStore,onBack:()->Unit,onDrawFurniture:(String)->Unit){androidx.compose.runtime.LaunchedEffect(Unit){onBack()}}
