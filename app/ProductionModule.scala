import java.time.Clock

import com.google.inject.AbstractModule
import services._

class ProductionModule extends AbstractModule {

  override def configure() = {
    bind(classOf[Queue]).to(classOf[DBQueue]).asEagerSingleton()
    bind(classOf[Dispatcher]).to(classOf[DBQueue])
    bind(classOf[LogStore]).to(classOf[SimpleLogStore])
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
  }

}
