package libs.solr.scala

import libs.solr.scala.query.ExpressionParser
import libs.solr.scala.query.QueryTemplate
import org.apache.solr.client.solrj.SolrServer
import org.apache.solr.common.params.CommonParams

class QueryBuilder(server: SolrServer, query: String)(implicit parser: ExpressionParser)
  extends QueryBuilderBase[QueryBuilder] {

  protected def createCopy = new QueryBuilder(server, query)(parser)

  /**
   * Returns the search result of this query as List[Map[String, Any]].
   *
   * @param params the parameter map or case class which would be given to the query
   * @return the search result
   */
  def getResultAsMap(params: Any = null): MapQueryResult = {
    solrQuery.setQuery(new QueryTemplate(query).merge(CaseClassMapper.toMap(params)))
    println("query= " + solrQuery.toString)
    responseToMap(server.query(solrQuery))
  }

  /**
   * Returns the search result of this query as the case class.
   *
   * @param params the parameter map or case class which would be given to the query
   * @return the search result
   */
  def getResultAs[T](params: Any = null)(implicit m: Manifest[T]): CaseClassQueryResult[T] = {
    solrQuery.setQuery(new QueryTemplate(query).merge(CaseClassMapper.toMap(params)))
    responseToObject[T](server.query(solrQuery))
  }


  override def toString: String = {
      this.query + "\n" + this.solrQuery.getRequestHandler
  }

}


