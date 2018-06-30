import filters.TokenAuthorizationFilter
import javax.inject._
import play.api._
import play.api.http.DefaultHttpFilters
import play.filters.cors.CORSFilter

@Singleton
class Filters @Inject()(env: Environment,
                        tokenAuthorizationFilter: TokenAuthorizationFilter,
                        corsFilter: CORSFilter)
  extends DefaultHttpFilters(corsFilter, tokenAuthorizationFilter) {
}
