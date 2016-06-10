package libs.solr.scala

import org.apache.solr.common.SolrDocumentList
import org.apache.solr.common.SolrDocument
import org.apache.solr.common.util.NamedList
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.response.QueryResponse
import scala.collection.JavaConverters._
import libs.solr.scala.Order


trait QueryBuilderBase[Repr <: QueryBuilderBase[Repr]] {

  protected var solrQuery = new SolrQuery()
  
  protected var id: String = "id"
  protected var highlightField: String = null
  protected var recommendFlag: Boolean = false

  protected def createCopy: Repr

  private def copy(newId: String = id, newHighlightField: String = highlightField,
                   newRecommendFlag: Boolean = recommendFlag): Repr = {
    val ret = createCopy
    ret.id = newId
    ret.highlightField = newHighlightField
    ret.recommendFlag = newRecommendFlag
    ret.solrQuery = solrQuery.getCopy
  
    ret
  }

  /**
   * Sets the field name of the unique key.
   * 
   * @param id the field name of the unique key (default is "id").
   */
  def id(id: String): Repr = {
    copy(newId = id)
  }
    
  /**
   * Sets field names to retrieve by this query.
   *
   * @param fields field names
   */
  def fields(fields: String*): Repr = {
    val ret = copy()
    fields.foreach { field =>
      ret.solrQuery.addField(field)
    }
    ret
  }

  /**
   * Sets the sorting field name and its order.
   *
   * @param field the sorting field name
   * @param order the sorting order
   */
  def sortBy(field: String, order: Order): Repr = {
    val ret = copy()
    ret.solrQuery.setSort(field, order)
    ret
  }

  /**
   * Sets grouping field names.
   *
   * @param fields field names
   */
  def groupBy(fields: String*): Repr = {
    val ret = copy()
    if(fields.size > 0){
      ret.solrQuery.setParam("group", "true")
      ret.solrQuery.setParam("group.field", fields: _*)
    }
    ret
  }
  
  /**
   * Sets parameter field names.
   *
   * @param field name 
   * @param value 
   */
  def setParameter(field:String,value:String ): Repr = {
       val ret = copy()
      ret.solrQuery.setParam(field, value)
    ret
  }

  /**
   * Sets facet field names.
   *
   * @param fields field names
   */
  def facetFields(fields: String*): Repr = {
    val ret = copy()
    ret.solrQuery.setFacet(true)
    ret.solrQuery.addFacetField(fields: _*)
    ret
  }
  
  /**
   * Sets sort facet [count,index].
   *
   * @param field field names
   */
  def sortFacet(field: String): Repr = {
    val ret = copy()
    ret.solrQuery.setFacetSort(field)
    ret
  }
  
  /**
   * Sets sort facet [count,index].
   *
   * @param field field names
   */
  def addFilterQuery(field: String): Repr = {
    val ret = copy()
    ret.solrQuery.addFilterQuery(field)
    ret
  }
  

  /**
   * Specifies the maximum number of results to return.
   * 
   * @param rows number of results
   */
  def rows(rows: Int): Repr = {
    val ret = copy()
    ret.solrQuery.setRows(rows)
    ret
  }
    
  /**
   * Sets the offset to start at in the result set.
   * 
   * @param start zero-based offset
   */
  def start(start: Int): Repr = {
    val ret = copy()
    ret.solrQuery.setStart(start)
    ret
  }
  
  /**
   * Configures to retrieve a highlighted snippet.
   * Highlighted snippet is set as the "highlight" property of the map or the case class.
   *
   * @param field the highlight field
   * @param size the highlight fragment size
   * @param prefix the prefix of highlighted ranges
   * @param postfix the postfix of highlighted ranges
   */
  def highlight(field: String, size: Int = 100, prefix: String = "", postfix: String = ""): Repr = {
    val ret = copy(newHighlightField = field)
    ret.solrQuery.setHighlight(true)
    ret.solrQuery.addHighlightField(field)
    ret.solrQuery.setHighlightSnippets(1)
    ret.solrQuery.setHighlightFragsize(size)
    if(prefix.nonEmpty){
      ret.solrQuery.setHighlightSimplePre(prefix)
    }
    if(postfix.nonEmpty){
      ret.solrQuery.setHighlightSimplePost(postfix)
    }
    ret
  }
  
  /**
   * Configure to recommendation search.
   * If you call this method, the query returns documents similar to the query result instead of them.
   * 
   * @param fields field names of recommendation target 
   */
  def recommend(fields: String*): Repr = {
    val ret = copy(newRecommendFlag = true)
    ret.solrQuery.set("mlt", true)
    ret.solrQuery.set("mlt.fl", fields.mkString(","))
    ret.solrQuery.set("mlt.mindf", 1)
    ret.solrQuery.set("mlt.mintf", 1)
    ret.solrQuery.set("mlt.count", 10)
    ret
  }

  protected def responseToMap(response: QueryResponse): MapQueryResult = {
    val highlight = response.getHighlighting()

    def toList(docList: SolrDocumentList): List[Map[String, Any]] = {
      (for(i <- 0 to docList.size() - 1) yield {
        val doc = docList.get(i)
        val map = doc.getFieldNames.asScala.map { key => (key, doc.getFieldValue(key)) }.toMap
        if(solrQuery.getHighlight){
          val id = doc.getFieldValue(this.id)
          if(id != null && highlight.get(id) != null && highlight.get(id).get(highlightField) != null){
            map + ("highlight" -> highlight.get(id).get(highlightField).get(0))
          } else {
            map + ("highlight" -> "")
          }
        } else {
          map
        }
      }).toList
    }

    val queryResult = if(recommendFlag){
      val mlt = response.getResponse.get("moreLikeThis").asInstanceOf[NamedList[Object]]
      val docs = mlt.getVal(0).asInstanceOf[java.util.List[SolrDocument]]
      docs.asScala.map { doc =>
        doc.getFieldNames.asScala.map { key => (key, doc.getFieldValue(key)) }.toMap
      }.toList
    } else { 
      solrQuery.getParams("group") match {
        case null => {
          toList(response.getResults())
        }
        case _ => {
           toList(response.getResults())
        }
      }
    }

    val facetResult = response.getFacetFields() match {
      case null => Map.empty[String, Map[String, Long]]
      case facetFields => facetFields.asScala.map { field => (
          field.getName(),
          field.getValues().asScala.map { value => (value.getName(), value.getCount()) }.toMap
      )}.toMap
    }

    val facetDates = response.getFacetDates() match {
      case null => Map.empty[String, Map[String, Long]]
      case facetFields => facetFields.asScala.map { field => (
          field.getName(),
          field.getValues().asScala.map { value => (value.getName(), value.getCount()) }.toMap
      )}.toMap
    }
    
    MapQueryResult(response.getResults().getNumFound(), queryResult, facetResult,facetDates)    
  }
  
  def responseToObject[T](response: QueryResponse)(implicit m: Manifest[T]): CaseClassQueryResult[T] = {
    val result = responseToMap(response)
    
    CaseClassQueryResult[T](
      result.numFound,
      result.documents.map { doc =>
        CaseClassMapper.map2class[T](doc)
      },
      result.facetFields,
      result.facetDates
    )
  }

}
