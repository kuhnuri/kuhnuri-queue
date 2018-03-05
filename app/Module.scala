import java.time.Clock

import com.google.inject.AbstractModule
import services._

class Module extends AbstractModule {

  override def configure() = {
//    bind(classOf[Queue]).to(classOf[DBQueue]).asEagerSingleton()
//    bind(classOf[Dispatcher]).to(classOf[DBQueue])
    bind(classOf[Queue]).to(classOf[SimpleQueue]).asEagerSingleton()
    bind(classOf[Dispatcher]).to(classOf[SimpleQueue])
    bind(classOf[LogStore]).to(classOf[SimpleLogStore])
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
  }

}
