package io.ebean.typequery;

import io.avaje.lang.Nullable;
import io.ebean.*;
import io.ebean.search.MultiMatch;
import io.ebean.search.TextCommonTerms;
import io.ebean.search.TextQueryString;
import io.ebean.search.TextSimple;
import io.ebean.text.PathProperties;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Query bean for strongly typed query construction and execution.
 * <p>
 * For each entity bean querybean-generator generates a query bean that implements QueryBean.
 *
 * <h2>Example - usage of QCustomer</h2>
 * <pre>{@code
 *
 *  Date fiveDaysAgo = ...
 *
 *  List<Customer> customers =
 *      new QCustomer()
 *        .name.ilike("rob")
 *        .status.equalTo(Customer.Status.GOOD)
 *        .registered.after(fiveDaysAgo)
 *        .contacts.email.endsWith("@foo.com")
 *        .orderBy()
 *          .name.asc()
 *          .registered.desc()
 *        .findList();
 *
 * }</pre>
 * <p>
 * <h2>Resulting SQL where</h2>
 * <pre>{@code
 *
 *   where lower(t0.name) like ? and t0.status = ? and t0.registered > ? and u1.email like ?
 *   order by t0.name, t0.registered desc;
 *
 *   --bind(rob,GOOD,Mon Jul 27 12:05:37 NZST 2015,%@foo.com)
 *
 * }</pre>
 *
 * @param <T> the entity bean type (normal entity bean type e.g. Customer)
 * @param <R> the specific query bean type (e.g. QCustomer)
 */
public interface QueryBean<T, R> {

  /**
   * Return the fetch group.
   */
  FetchGroup<T> buildFetchGroup();

  /**
   * Return a copy of the query bean.
   */
  R copy();

  /**
   * Return the underlying query.
   * <p>
   * Generally it is not expected that you will need to do this but typically use
   * the find methods available on this 'root query bean' instance like findList().
   */
  Query<T> query();

  /**
   * Explicitly set a comma delimited list of the properties to fetch on the
   * 'main' root level entity bean (aka partial object). Note that '*' means all
   * properties.
   * <p>
   * You use {@link #fetch(String, String)} to specify specific properties to fetch
   * on other non-root level paths of the object graph.
   * <p>
   * <pre>{@code
   *
   * List<Customer> customers =
   *     new QCustomer()
   *     // Only fetch the customer id, name and status.
   *     // This is described as a "Partial Object"
   *     .select("name, status")
   *     .name.ilike("rob%")
   *     .findList();
   *
   * }</pre>
   *
   * @param properties the properties to fetch for this bean (* = all properties).
   */
  R select(String properties);

  /**
   * Set a FetchGroup to control what part of the object graph is loaded.
   * <p>
   * FetchGroup is immutable and threadsafe. We expect to create and store
   * FetchGroup to a static final field and reuse the instance.
   * <p>
   * FetchGroup is an alternative to using select() and fetch() providing a nice
   * clean separation between what a query should load and the query predicates.
   *
   * <pre>{@code
   *
   * // immutable threadsafe
   *
   * static final FetchGroup<Customer> fetchGroup =
   *   QCustomer.forFetchGroup()
   *     .shippingAddress.fetch()
   *     .contacts.fetch()
   *     .buildFetchGroup();
   *
   * List<Customer> customers = new QCustomer()
   *   .select(fetchGroup)
   *   .findList();
   *
   * }</pre>
   *
   *
   * <pre>{@code
   *
   * static final FetchGroup<Customer> fetchGroup =
   *   FetchGroup.of(Customer.class)
   *     .select("name, status")
   *     .fetch("contacts", "firstName, lastName, email")
   *     .build();
   *
   * List<Customer> customers = new QCustomer()
   *   .select(fetchGroup)
   *   .findList();
   *
   * }</pre>
   */
  R select(FetchGroup<T> fetchGroup);

  /**
   * Specify the properties to be loaded on the 'main' root level entity bean.
   * <p>
   * The resulting entities with be "partially loaded" aka partial objects.
   * <p>
   * Alternatively we can use a {@link #select(FetchGroup)} to specify all properties
   * to load on all parts of the graph.
   *
   * <pre>{@code
   *
   *   // alias for the customer properties in select()
   *   QCustomer cust = QCustomer.alias();
   *
   *   // alias for the contact properties in contacts.fetch()
   *   QContact contact = QContact.alias();
   *
   *   List<Customer> customers =
   *     new QCustomer()
   *       // specify the parts of the graph we want to load
   *       .select(cust.id, cust.name)
   *       .contacts.fetch(contact.firstName, contact.lastName, contact.email)
   *
   *       // predicates
   *       .id.gt(1)
   *       .findList();
   *
   * }</pre>
   *
   * @param properties the list of properties to fetch
   */
  @SuppressWarnings("unchecked")
  R select(TQProperty<R, ?>... properties);

  /**
   * Specify the properties to be loaded on the 'main' root level entity bean
   * also allowing for functions to be used like {@link StdOperators#max(Query.Property)}.
   *
   * @param properties the list of properties to fetch
   */
  R select(Query.Property<?>... properties);

  /**
   * Specify a path to load including all its properties.
   *
   * <pre>{@code
   *
   * List<Customer> customers =
   *     new QCustomer()
   *     // eager fetch the contacts
   *     .fetch("contacts")
   *     .findList();
   *
   * }</pre>
   *
   * @param path the property path of an associated (OneToOne, OneToMany, ManyToOne or ManyToMany) bean.
   */
  R fetch(String path);

  /**
   * Specify a path to load including all its properties using a "query join".
   *
   * <pre>{@code
   *
   * List<Customer> customers =
   *     new QCustomer()
   *     // eager fetch the contacts using a "query join"
   *     .fetchQuery("contacts")
   *     .findList();
   *
   * }</pre>
   *
   * @param path the property path of an associated (OneToOne, OneToMany, ManyToOne or ManyToMany) bean.
   */
  R fetchQuery(String path);

  /**
   * Specify a path to load from L2 cache including all its properties.
   *
   * <pre>{@code
   *
   * List<Order> orders =
   *     new QOrder()
   *     // eager fetch the customer using L2 cache
   *     .fetchCache("customer")
   *     .findList();
   *
   * }</pre>
   *
   * @param path the property path to load from L2 cache.
   */
  R fetchCache(String path);

  /**
   * Specify a path and properties to load using a "query join".
   *
   * <pre>{@code
   *
   * List<Customer> customers =
   *     new QCustomer()
   *     // eager fetch contacts using a "query join"
   *     .fetchQuery("contacts", "email, firstName, lastName")
   *     .findList();
   *
   * }</pre>
   *
   * @param path       the property path of an associated (OneToOne, OneToMany, ManyToOne or ManyToMany) bean.
   * @param properties the properties to load for this path.
   */
  R fetchQuery(String path, String properties);

  /**
   * Specify a path and properties to load from L2 cache.
   *
   * <pre>{@code
   *
   * List<Order> orders =
   *     new QOrder()
   *     // eager fetch the customer using L2 cache
   *     .fetchCache("customer", "name,status")
   *     .findList();
   *
   * }</pre>
   *
   * @param path       the property path to load from L2 cache.
   * @param properties the properties to load for this path.
   */
  R fetchCache(String path, String properties);

  /**
   * Specify a path to <em>fetch</em> with its specific properties to include
   * (aka partial object).
   * <p>
   * When you specify a join this means that property (associated bean(s)) will
   * be fetched and populated. If you specify "*" then all the properties of the
   * associated bean will be fetched and populated. You can specify a comma
   * delimited list of the properties of that associated bean which means that
   * only those properties are fetched and populated resulting in a
   * "Partial Object" - a bean that only has some of its properties populated.
   * <p>
   * <pre>{@code
   *
   * // query orders...
   * List<Order> orders =
   *     new QOrder()
   *       .fetch("customer", "name, phoneNumber")
   *       .fetch("customer.billingAddress", "*")
   *       .findList();
   *
   * }</pre>
   * <p>
   * If columns is null or "*" then all columns/properties for that path are fetched.
   *
   * <pre>{@code
   *
   * List<Customer> customers =
   *     new QCustomer()
   *     .select("name, status")
   *     .fetch("contacts", "firstName,lastName,email")
   *     .findList();
   *
   * }</pre>
   *
   * @param path       the path of an associated (OneToOne, OneToMany, ManyToOne or ManyToMany) bean.
   * @param properties properties of the associated bean that you want to include in the
   *                   fetch (* means all properties, null also means all properties).
   */
  R fetch(String path, String properties);

  /**
   * Additionally specify a FetchConfig to use a separate query or lazy loading
   * to load this path.
   * <p>
   * <pre>{@code
   *
   * // fetch customers (their id, name and status)
   * List<Customer> customers =
   *     new QCustomer()
   *     .select("name, status")
   *     .fetch("contacts", "firstName,lastName,email", new FetchConfig().lazy(10))
   *     .findList();
   *
   * }</pre>
   */
  R fetch(String path, String properties, FetchConfig fetchConfig);

  /**
   * Additionally specify a FetchConfig to specify a "query join" and or define
   * the lazy loading query.
   * <p>
   * <pre>{@code
   *
   * // fetch customers (their id, name and status)
   * List<Customer> customers =
   *     new QCustomer()
   *       // lazy fetch contacts with a batch size of 100
   *       .fetch("contacts", new FetchConfig().lazy(100))
   *       .findList();
   *
   * }</pre>
   */
  R fetch(String path, FetchConfig fetchConfig);

  /**
   * Apply the path properties replacing the select and fetch clauses.
   * <p>
   * This is typically used when the PathProperties is applied to both the query and the JSON output.
   */
  R apply(PathProperties pathProperties);

  /**
   * Apply changes to the query conditional on the supplied predicate.
   * <p>
   * Typically, the changes are extra predicates etc.
   *
   * @param predicate The predicate which when true the changes are applied
   * @param apply     The changes to apply to the query
   */
  R alsoIf(BooleanSupplier predicate, Consumer<R> apply);

  /**
   * Perform an 'As of' query using history tables to return the object graph
   * as of a time in the past.
   * <p>
   * To perform this query the DB must have underlying history tables.
   *
   * @param asOf the date time in the past at which you want to view the data
   */
  R asOf(Timestamp asOf);

  /**
   * Execute the query against the draft set of tables.
   */
  R asDraft();

  /**
   * Execute the query including soft deleted rows.
   */
  R setIncludeSoftDeletes();

  /**
   * Add an expression to the WHERE or HAVING clause.
   */
  R add(Expression expression);

  /**
   * Set root table alias.
   */
  R alias(String alias);

  /**
   * Set the maximum number of rows to return in the query.
   *
   * @param maxRows the maximum number of rows to return in the query.
   */
  R setMaxRows(int maxRows);

  /**
   * Set the first row to return for this query.
   *
   * @param firstRow the first row to include in the query result.
   */
  R setFirstRow(int firstRow);

  /**
   * Execute the query allowing properties with invalid JSON to be collected and not fail the query.
   * <pre>{@code
   *
   *   // fetch a bean with JSON content
   *   EBasicJsonList bean= new QEBasicJsonList()
   *       .id.equalTo(42)
   *       .setAllowLoadErrors()  // collect errors into bean state if we have invalid JSON
   *       .findOne();
   *
   *
   *   // get the invalid JSON errors from the bean state
   *   Map<String, Exception> errors = server().getBeanState(bean).getLoadErrors();
   *
   *   // If this map is not empty tell we have invalid JSON
   *   // and should try and fix the JSON content or inform the user
   *
   * }</pre>
   */
  R setAllowLoadErrors();

  /**
   * Explicitly specify whether to use AutoTune for this query.
   * <p>
   * If you do not call this method on a query the "Implicit AutoTune mode" is
   * used to determine if AutoTune should be used for a given query.
   * <p>
   * AutoTune can add additional fetch paths to the query and specify which
   * properties are included for each path. If you have explicitly defined some
   * fetch paths AutoTune will not remove them.
   */
  R setAutoTune(boolean autoTune);

  /**
   * A hint which for JDBC translates to the Statement.fetchSize().
   * <p>
   * Gives the JDBC driver a hint as to the number of rows that should be
   * fetched from the database when more rows are needed for ResultSet.
   */
  R setBufferFetchSizeHint(int fetchSize);

  /**
   * Set whether this query uses DISTINCT.
   */
  R setDistinct(boolean distinct);

  /**
   * Set the index(es) to search for a document store which uses partitions.
   * <p>
   * For example, when executing a query against ElasticSearch with daily indexes we can
   * explicitly specify the indexes to search against.
   *
   * <pre>{@code
   *
   *   // explicitly specify the indexes to search
   *   query.setDocIndexName("logstash-2016.11.5,logstash-2016.11.6")
   *
   *   // search today's index
   *   query.setDocIndexName("$today")
   *
   *   // search the last 3 days
   *   query.setDocIndexName("$last-3")
   *
   * }</pre>
   * <p>
   * If the indexName is specified with ${daily} e.g. "logstash-${daily}" ... then we can use
   * $today and $last-x as the search docIndexName like the examples below.
   * </p>
   * <pre>{@code
   *
   *   // search today's index
   *   query.setDocIndexName("$today")
   *
   *   // search the last 3 days
   *   query.setDocIndexName("$last-3")
   *
   * }</pre>
   *
   * @param indexName The index or indexes to search against
   * @return This query
   */
  R setDocIndexName(String indexName);

  /**
   * Restrict the query to only return subtypes of the given inherit type.
   * <pre>{@code
   *
   *   List<Animal> animals =
   *     new QAnimal()
   *       .name.startsWith("Fluffy")
   *       .setInheritType(Cat.class)
   *       .findList();
   *
   * }</pre>
   */
  R setInheritType(Class<? extends T> type);

  /**
   * Set the base table to use for this query.
   * <p>
   * Typically this is used when a table has partitioning and we wish to specify a specific
   * partition/table to query against.
   * </p>
   * <pre>{@code
   *
   *   QOrder()
   *   .setBaseTable("order_2019_05")
   *   .status.equalTo(Status.NEW)
   *   .findList();
   *
   * }</pre>
   */
  R setBaseTable(String baseTable);

  /**
   * Execute the query with the given lock type and WAIT.
   * <p>
   * Note that <code>forUpdate()</code> is the same as
   * <code>withLock(LockType.UPDATE)</code>.
   * <p>
   * Provides us with the ability to explicitly use Postgres
   * SHARE, KEY SHARE, NO KEY UPDATE and UPDATE row locks.
   */
  R withLock(Query.LockType lockType);

  /**
   * Execute the query with the given lock type and lock wait.
   * <p>
   * Note that <code>forUpdateNoWait()</code> is the same as
   * <code>withLock(LockType.UPDATE, LockWait.NOWAIT)</code>.
   * <p>
   * Provides us with the ability to explicitly use Postgres
   * SHARE, KEY SHARE, NO KEY UPDATE and UPDATE row locks.
   */
  R withLock(Query.LockType lockType, Query.LockWait lockWait);

  /**
   * Add EXISTS sub-query predicate.
   */
  R exists(Query<?> subQuery);

  /**
   * Add NOT EXISTS sub-query predicate.
   */
  R notExists(Query<?> subQuery);

  /**
   * EXISTS using a SQL SubQuery.
   *
   * @param sqlSubQuery The SQL SubQuery
   * @param bindValues  Optional bind values if the SubQuery uses {@code ? } bind values.
   */
  R exists(String sqlSubQuery, Object... bindValues);

  /**
   * Not EXISTS using a SQL SubQuery.
   *
   * @param sqlSubQuery The SQL SubQuery
   * @param bindValues  Optional bind values if the SubQuery uses {@code ? } bind values.
   */
  R notExists(String sqlSubQuery, Object... bindValues);

  /**
   * Execute using "for update" clause which results in the DB locking the record.
   */
  R forUpdate();

  /**
   * Execute using "for update" clause with "no wait" option.
   * <p>
   * This is typically a Postgres and Oracle only option at this stage.
   */
  R forUpdateNoWait();

  /**
   * Execute using "for update" clause with "skip locked" option.
   * <p>
   * This is typically a Postgres and Oracle only option at this stage.
   */
  R forUpdateSkipLocked();

  /**
   * Return this query as an UpdateQuery.
   *
   * <pre>{@code
   *
   *   int rows =
   *     new QCustomer()
   *     .name.startsWith("Rob")
   *     .organisation.id.equalTo(42)
   *     .asUpdate()
   *       .set("active", false)
   *       .update()
   *
   * }</pre>
   *
   * @return This query as an UpdateQuery
   */
  UpdateQuery<T> asUpdate();

  /**
   * Convert the query to a DTO bean query.
   * <p>
   * We effectively use the underlying ORM query to build the SQL and then execute
   * and map it into DTO beans.
   */
  <D> DtoQuery<D> asDto(Class<D> dtoClass);

  /**
   * Set the Id value to query. This is used with findOne().
   * <p>
   * You can use this to have further control over the query. For example adding fetch joins.
   * <p>
   * <pre>{@code
   *
   * Order order =
   *   new QOrder()
   *     .setId(1)
   *     .fetch("details")
   *     .findOne();
   *
   * // the order details were eagerly fetched
   * List<OrderDetail> details = order.getDetails();
   *
   * }</pre>
   */
  R setId(Object id);

  /**
   * Set a list of Id values to match.
   * <p>
   * <pre>{@code
   *
   * List<Order> orders =
   *   new QOrder()
   *     .setIdIn(42, 43, 44)
   *     .findList();
   *
   * }</pre>
   */
  R setIdIn(Object... ids);

  /**
   * Set a collection of Id values to match.
   * <p>
   * <pre>{@code
   *
   * Collection<?> ids = ...
   *
   * List<Order> orders =
   *   new QOrder()
   *     .setIdIn(ids)
   *     .findList();
   *
   * }</pre>
   */
  R setIdIn(Collection<?> ids);

  /**
   * Set a label on the query.
   * <p>
   * This label can be used to help identify query performance metrics but we can also use
   * profile location enhancement on Finders so for some that would be a better option.
   */
  R setLabel(String label);

  /**
   * Set a SQL query hint.
   * <p>
   * This results in an inline comment that immediately follows
   * after the select keyword in the form: {@code /*+ hint *\/ }
   */
  R setHint(String hint);

  /**
   * Set the profile location.
   * <p>
   * This is typically set automatically via enhancement when profile location enhancement
   * is turned on. It is generally not set by application code.
   */
  R setProfileLocation(ProfileLocation profileLocation);

  /**
   * Set the default lazy loading batch size to use.
   * <p>
   * When lazy loading is invoked on beans loaded by this query then this sets the
   * batch size used to load those beans.
   *
   * @param lazyLoadBatchSize the number of beans to lazy load in a single batch
   */
  R setLazyLoadBatchSize(int lazyLoadBatchSize);

  /**
   * Set the property to use as keys for a map.
   * <p>
   * If no property is set then the id property is used.
   * <p>
   * <pre>{@code
   *
   * // Assuming sku is unique for products...
   *
   * Map<String,Product> productMap =
   *     new QProduct()
   *     // use sku for keys...
   *     .setMapKey("sku")
   *     .findMap();
   *
   * }</pre>
   *
   * @param mapKey the property to use as keys for a map.
   */
  R setMapKey(String mapKey);

  /**
   * Specify the PersistenceContextScope to use for this query.
   * <p>
   * When this is not set the 'default' configured on {@link io.ebean.config.DatabaseConfig#setPersistenceContextScope(PersistenceContextScope)}
   * is used - this value defaults to {@link PersistenceContextScope#TRANSACTION}.
   * <p>
   * Note that the same persistence Context is used for subsequent lazy loading and query join queries.
   * <p>
   * Note that #findEach uses a 'per object graph' PersistenceContext so this scope is ignored for
   * queries executed as #findIterate, #findEach, #findEachWhile.
   *
   * @param scope The scope to use for this query and subsequent lazy loading.
   */
  R setPersistenceContextScope(PersistenceContextScope scope);

  /**
   * Set RawSql to use for this query.
   */
  R setRawSql(RawSql rawSql);

  /**
   * When set to true when you want the returned beans to be read only.
   */
  R setReadOnly(boolean readOnly);

  /**
   * Set this to true to use the bean cache.
   * <p>
   * If the query result is in cache then by default this same instance is
   * returned. In this sense it should be treated as a read only object graph.
   */
  R setUseCache(boolean useCache);

  /**
   * Set the mode to use the bean cache when executing this query.
   * <p>
   * By default, "find by id" and "find by natural key" will use the bean cache
   * when bean caching is enabled. Setting this to false means that the query
   * will not use the bean cache and instead hit the database.
   * <p>
   * By default, findList() with natural keys will not use the bean cache. In that
   * case we need to explicitly use the bean cache.
   */
  R setBeanCacheMode(CacheMode beanCacheMode);

  /**
   * Set to true if this query should execute against the doc store.
   * <p>
   * When setting this you may also consider disabling lazy loading.
   */
  R setUseDocStore(boolean useDocStore);

  /**
   * Set true if you want to disable lazy loading.
   * <p>
   * That is, once the object graph is returned further lazy loading is disabled.
   */
  R setDisableLazyLoading(boolean disableLazyLoading);

  /**
   * Disable read auditing for this query.
   * <p>
   * This is intended to be used when the query is not a user initiated query and instead
   * part of the internal processing in an application to load a cache or document store etc.
   * In these cases we don't want the query to be part of read auditing.
   */
  R setDisableReadAuditing();

  /**
   * Set this to true to use the query cache.
   */
  R setUseQueryCache(boolean useCache);

  /**
   * Set the {@link CacheMode} to use the query for executing this query.
   */
  R setUseQueryCache(CacheMode cacheMode);

  /**
   * Set a timeout on this query.
   * <p>
   * This will typically result in a call to setQueryTimeout() on a
   * preparedStatement. If the timeout occurs an exception will be thrown - this
   * will be a SQLException wrapped up in a PersistenceException.
   *
   * @param secs the query timeout limit in seconds. Zero means there is no limit.
   */
  R setTimeout(int secs);

  /**
   * Returns the set of properties or paths that are unknown (do not map to known properties or paths).
   * <p>
   * Validate the query checking the where and orderBy expression paths to confirm if
   * they represent valid properties or paths for the given bean type.
   */
  Set<String> validate();

  /**
   * Add raw expression with no parameters.
   * <p>
   * When properties in the clause are fully qualified as table-column names
   * then they are not translated. logical property name names (not fully
   * qualified) will still be translated to their physical name.
   * <p>
   * <pre>{@code
   *
   *   raw("orderQty < shipQty")
   *
   * }</pre>
   *
   * <h4>Subquery example:</h4>
   * <pre>{@code
   *
   *   .raw("t0.customer_id in (select customer_id from customer_group where group_id = any(?::uuid[]))", groupIds)
   *
   * }</pre>
   */
  R raw(String rawExpression);

  /**
   * Add raw expression with an array of parameters.
   * <p>
   * The raw expression should contain the same number of ? as there are
   * parameters.
   * <p>
   * When properties in the clause are fully qualified as table-column names
   * then they are not translated. logical property name names (not fully
   * qualified) will still be translated to their physical name.
   */
  R raw(String rawExpression, Object... bindValues);

  /**
   * Only add the raw expression if the values is not null or empty.
   * <p>
   * This is a pure convenience expression to make it nicer to deal with the pattern where we use
   * raw() expression with a subquery and only want to add the subquery predicate when the collection
   * of values is not empty.
   *
   * <h3>Without inOrEmpty()</h3>
   * <pre>{@code
   *
   *   QCustomer query = new QCustomer() // add some predicates
   *     .status.equalTo(Status.NEW);
   *
   *   // common pattern - we can use rawOrEmpty() instead
   *   if (orderIds != null && !orderIds.isEmpty()) {
   *     query.raw("t0.customer_id in (select o.customer_id from orders o where o.id in (?1))", orderIds);
   *   }
   *
   *   query.findList();
   *
   * }</pre>
   *
   * <h3>Using rawOrEmpty()</h3>
   * Note that in the example below we use the <code>?1</code> bind parameter to get  "parameter expansion"
   * for each element in the collection.
   *
   * <pre>{@code
   *
   *   new QCustomer()
   *     .status.equalTo(Status.NEW)
   *     // only add the expression if orderIds is not empty
   *     .rawOrEmpty("t0.customer_id in (select o.customer_id from orders o where o.id in (?1))", orderIds);
   *     .findList();
   *
   * }</pre>
   *
   * <h3>Postgres ANY</h3>
   * With Postgres we would often use the SQL <code>ANY</code> expression and array parameter binding
   * rather than <code>IN</code>.
   *
   * <pre>{@code
   *
   *   new QCustomer()
   *     .status.equalTo(Status.NEW)
   *     .rawOrEmpty("t0.customer_id in (select o.customer_id from orders o where o.id = any(?))", orderIds);
   *     .findList();
   *
   * }</pre>
   * <p>
   * Note that we need to cast the Postgres array for UUID types like:
   * </p>
   * <pre>{@code
   *
   *   " ... = any(?::uuid[])"
   *
   * }</pre>
   *
   * @param raw    The raw expression that is typically a subquery
   * @param values The values which is typically a list or set of id values.
   */
  R rawOrEmpty(String raw, Collection<?> values);

  /**
   * Add raw expression with a single parameter.
   * <p>
   * The raw expression should contain a single ? at the location of the
   * parameter.
   * <p>
   * When properties in the clause are fully qualified as table-column names
   * then they are not translated. logical property name names (not fully
   * qualified) will still be translated to their physical name.
   * <p>
   * <h4>Example:</h4>
   * <pre>{@code
   *
   *   // use a database function
   *   raw("add_days(orderDate, 10) < ?", someDate)
   *
   * }</pre>
   *
   * <h4>Subquery example:</h4>
   * <pre>{@code
   *
   *   .raw("t0.customer_id in (select customer_id from customer_group where group_id = any(?::uuid[]))", groupIds)
   *
   * }</pre>
   */
  R raw(String rawExpression, Object bindValue);

  /**
   * In expression using multiple columns.
   */
  R inTuples(InTuples inTuples);

  /**
   * Marker that can be used to indicate that the order by clause is defined after this.
   * <p>
   * <h2>Example: order by customer name, order date</h2>
   * <pre>{@code
   *   List<Order> orders =
   *          new QOrder()
   *            .customer.name.ilike("rob")
   *            .orderBy()
   *              .customer.name.asc()
   *              .orderDate.asc()
   *            .findList();
   *
   * }</pre>
   */
  R orderBy();

  /**
   * @deprecated migrate to {@link #orderBy()}.
   */
  @Deprecated(since = "13.19", forRemoval = true)
  R order();

  /**
   * Set the full raw order by clause replacing the existing order by clause if there is one.
   * <p>
   * This follows SQL syntax using commas between each property with the
   * optional asc and desc keywords representing ascending and descending order
   * respectively.
   */
  R orderBy(String orderByClause);

  /**
   * @deprecated migrate to {@link #orderBy(String)}
   */
  @Deprecated(since = "13.19", forRemoval = true)
  R order(String orderByClause);

  /**
   * Begin a list of expressions added by 'OR'.
   * <p>
   * Use endOr() or endJunction() to stop added to OR and 'pop' to the parent expression list.
   * <p>
   * <h2>Example</h2>
   * <p>
   * This example uses an 'OR' expression list with an inner 'AND' expression list.
   * </p>
   * <pre>{@code
   *
   *    List<Customer> customers =
   *          new QCustomer()
   *            .status.equalTo(Customer.Status.GOOD)
   *            .or()
   *              .id.greaterThan(1000)
   *              .and()
   *                .name.startsWith("super")
   *                .registered.after(fiveDaysAgo)
   *              .endAnd()
   *            .endOr()
   *            .orderBy().id.desc()
   *            .findList();
   *
   * }</pre>
   * <h2>Resulting SQL where clause</h2>
   * <pre>{@code sql
   *
   *    where t0.status = ?  and (t0.id > ?  or (t0.name like ?  and t0.registered > ? ) )
   *    order by t0.id desc;
   *
   *    --bind(GOOD,1000,super%,Wed Jul 22 00:00:00 NZST 2015)
   *
   * }</pre>
   */
  R or();

  /**
   * Begin a list of expressions added by 'AND'.
   * <p>
   * Use endAnd() or endJunction() to stop added to AND and 'pop' to the parent expression list.
   * <p>
   * Note that typically the AND expression is only used inside an outer 'OR' expression.
   * This is because the top level expression list defaults to an 'AND' expression list.
   *
   * <h2>Example</h2>
   * <p>
   * This example uses an 'OR' expression list with an inner 'AND' expression list.
   * </p>
   * <pre>{@code
   *
   *    List<Customer> customers =
   *          new QCustomer()
   *            .status.equalTo(Customer.Status.GOOD)
   *            .or() // OUTER 'OR'
   *              .id.greaterThan(1000)
   *              .and()  // NESTED 'AND' expression list
   *                .name.startsWith("super")
   *                .registered.after(fiveDaysAgo)
   *                .endAnd()
   *              .endOr()
   *            .orderBy().id.desc()
   *            .findList();
   *
   * }</pre>
   * <h2>Resulting SQL where clause</h2>
   * <pre>{@code sql
   *
   *    where t0.status = ?  and (t0.id > ?  or (t0.name like ?  and t0.registered > ? ) )
   *    order by t0.id desc;
   *
   *    --bind(GOOD,1000,super%,Wed Jul 22 00:00:00 NZST 2015)
   *
   * }</pre>
   */
  R and();

  /**
   * Begin a list of expressions added by NOT.
   * <p>
   * Use endNot() or endJunction() to stop added to NOT and 'pop' to the parent expression list.
   */
  R not();

  /**
   * Begin a list of expressions added by MUST.
   * <p>
   * This automatically makes this query a document store query.
   * <p>
   * Use endJunction() to stop added to MUST and 'pop' to the parent expression list.
   */
  R must();

  /**
   * Begin a list of expressions added by MUST NOT.
   * <p>
   * This automatically makes this query a document store query.
   * <p>
   * Use endJunction() to stop added to MUST NOT and 'pop' to the parent expression list.
   */
  R mustNot();

  /**
   * Begin a list of expressions added by SHOULD.
   * <p>
   * This automatically makes this query a document store query.
   * <p>
   * Use endJunction() to stop added to SHOULD and 'pop' to the parent expression list.
   */
  R should();

  /**
   * End a list of expressions added by 'OR'.
   */
  R endJunction();

  /**
   * End OR junction - synonym for endJunction().
   */
  R endOr();

  /**
   * End AND junction - synonym for endJunction().
   */
  R endAnd();

  /**
   * End NOT junction - synonym for endJunction().
   */
  R endNot();

  /**
   * Add expression after this to the WHERE expression list.
   * <p>
   * For queries against the normal database (not the doc store) this has no effect.
   * <p>
   * This is intended for use with Document Store / ElasticSearch where expressions can be put into either
   * the "query" section or the "filter" section of the query. Full text expressions like MATCH are in the
   * "query" section but many expression can be in either - expressions after the where() are put into the
   * "filter" section which means that they don't add to the relevance and are also cache-able.
   */
  R where();

  /**
   * Begin added expressions to the 'Text' expression list.
   * <p>
   * This automatically makes the query a document store query.
   * <p>
   * For ElasticSearch expressions added to 'text' go into the ElasticSearch 'query context'
   * and expressions added to 'where' go into the ElasticSearch 'filter context'.
   */
  R text();

  /**
   * Add a Text Multi-match expression (document store only).
   * <p>
   * This automatically makes the query a document store query.
   */
  R multiMatch(String query, MultiMatch multiMatch);

  /**
   * Add a Text Multi-match expression (document store only).
   * <p>
   * This automatically makes the query a document store query.
   */
  R multiMatch(String query, String... properties);

  /**
   * Add a Text common terms expression (document store only).
   * <p>
   * This automatically makes the query a document store query.
   */
  R textCommonTerms(String query, TextCommonTerms options);

  /**
   * Add a Text simple expression (document store only).
   * <p>
   * This automatically makes the query a document store query.
   */
  R textSimple(String query, TextSimple options);

  /**
   * Add a Text query string expression (document store only).
   * <p>
   * This automatically makes the query a document store query.
   */
  R textQueryString(String query, TextQueryString options);

  /**
   * Execute the query using the given transaction.
   */
  R usingTransaction(Transaction transaction);

  /**
   * Execute the query using the given connection.
   */
  R usingConnection(Connection connection);

  /**
   * Ensure that the master DataSource is used if there is a read only data source
   * being used (that is using a read replica database potentially with replication lag).
   * <p>
   * When the database is configured with a read-only DataSource via
   * say {@link io.ebean.config.DatabaseConfig#setReadOnlyDataSource(DataSource)} then
   * by default when a query is run without an active transaction, it uses the read-only data
   * source. We we use {@code usingMaster()} to instead ensure that the query is executed
   * against the master data source.
   */
  R usingMaster();

  /**
   * Execute the query returning true if a row is found.
   * <p>
   * The query is executed using max rows of 1 and will only select the id property.
   * This method is really just a convenient way to optimise a query to perform a
   * 'does a row exist in the db' check.
   *
   * <h2>Example using a query bean:</h2>
   * <pre>{@code
   *
   *   boolean userExists =
   *     new QContact()
   *       .email.equalTo("rob@foo.com")
   *       .exists();
   *
   * }</pre>
   *
   * <h2>Example:</h2>
   * <pre>{@code
   *
   *   boolean userExists = query()
   *     .where().eq("email", "rob@foo.com")
   *     .exists();
   *
   * }</pre>
   *
   * @return True if the query finds a matching row in the database
   */
  boolean exists();

  /**
   * Execute the query returning either a single bean or null (if no matching
   * bean is found).
   * <p>
   * If more than 1 row is found for this query then a PersistenceException is
   * thrown.
   * <p>
   * This is useful when your predicates dictate that your query should only
   * return 0 or 1 results.
   * <p>
   * <pre>{@code
   *
   * // assuming the sku of products is unique...
   * Product product =
   *     new QProduct()
   *         .sku.equalTo("aa113")
   *         .findOne();
   * ...
   * }</pre>
   * <p>
   * It is also useful with finding objects by their id when you want to specify
   * further join information to optimise the query.
   * <p>
   * <pre>{@code
   *
   * // Fetch order 42 and additionally fetch join its order details...
   * Order order =
   *     new QOrder()
   *         .fetch("details") // eagerly load the order details
   *         .id.equalTo(42)
   *         .findOne();
   *
   * // the order details were eagerly loaded
   * List<OrderDetail> details = order.getDetails();
   * ...
   * }</pre>
   */
  @Nullable
  T findOne();

  /**
   * Execute the query returning an optional bean.
   */
  Optional<T> findOneOrEmpty();

  /**
   * Execute the query returning the list of objects.
   * <p>
   * This query will execute against the EbeanServer that was used to create it.
   * <p>
   * <pre>{@code
   *
   * List<Customer> customers =
   *     new QCustomer()
   *       .name.ilike("rob%")
   *       .findList();
   *
   * }</pre>
   *
   * @see Query#findList()
   */
  List<T> findList();

  /**
   * Execute the query returning the result as a Stream.
   * <p>
   * Note that this can support very large queries iterating
   * any number of results. To do so internally it can use
   * multiple persistence contexts.
   * </p>
   * <pre>{@code
   *
   *  // use try with resources to ensure Stream is closed
   *
   *  try (Stream<Customer> stream = query.findStream()) {
   *    stream
   *    .map(...)
   *    .collect(...);
   *  }
   *
   * }</pre>
   */
  Stream<T> findStream();

  /**
   * Execute the query returning the set of objects.
   * <p>
   * This query will execute against the EbeanServer that was used to create it.
   * <p>
   * <pre>{@code
   *
   * Set<Customer> customers =
   *     new QCustomer()
   *       .name.ilike("rob%")
   *       .findSet();
   *
   * }</pre>
   *
   * @see Query#findSet()
   */
  Set<T> findSet();

  /**
   * Execute the query returning the list of Id's.
   * <p>
   * This query will execute against the EbeanServer that was used to create it.
   *
   * @see Query#findIds()
   */
  <A> List<A> findIds();

  /**
   * Execute the query returning a map of the objects.
   * <p>
   * This query will execute against the EbeanServer that was used to create it.
   * <p>
   * You can use setMapKey() or asMapKey() to specify the property to be used as keys
   * on the map. If one is not specified then the id property is used.
   * <p>
   * <pre>{@code
   *
   * Map<String, Product> map =
   *   new QProduct()
   *     .sku.asMapKey()
   *     .findMap();
   *
   * }</pre>
   *
   * @see Query#findMap()
   */
  <K> Map<K, T> findMap();

  /**
   * Execute the query iterating over the results.
   * <p>
   * Note that findIterate (and findEach and findEachWhile) uses a "per graph"
   * persistence context scope and adjusts jdbc fetch buffer size for large
   * queries. As such it is better to use findList for small queries.
   * <p>
   * Remember that with {@link QueryIterator} you must call {@link QueryIterator#close()}
   * when you have finished iterating the results (typically in a finally block).
   * <p>
   * findEach() and findEachWhile() are preferred to findIterate() as they ensure
   * the jdbc statement and resultSet are closed at the end of the iteration.
   * <p>
   * This query will execute against the EbeanServer that was used to create it.
   * </p>
   * <pre>{@code
   *
   *  Query<Customer> query =
   *    new QCustomer()
   *     .status.equalTo(Customer.Status.NEW)
   *     .orderBy()
   *       id.asc()
   *     .query();
   *
   *  try (QueryIterator<Customer> it = query.findIterate()) {
   *    while (it.hasNext()) {
   *      Customer customer = it.next();
   *      // do something with customer ...
   *    }
   *  }
   *
   * }</pre>
   */
  QueryIterator<T> findIterate();

  /**
   * Execute the query returning a list of values for a single property.
   * <p>
   * <h3>Example</h3>
   * <pre>{@code
   *
   *  List<String> names =
   *    new QCustomer()
   *      .setDistinct(true)
   *      .select(name)
   *      .findSingleAttributeList();
   *
   * }</pre>
   *
   * @return the list of values for the selected property
   */
  <A> List<A> findSingleAttributeList();

  /**
   * Execute the query returning a single value or null for a single property.
   * <p>
   * <h3>Example</h3>
   * <pre>{@code
   *
   *  LocalDate maxDate =
   *    new QCustomer()
   *      .select("max(startDate)")
   *      .findSingleAttribute();
   *
   * }</pre>
   *
   * @return a single value or null for the selected property
   */
  @Nullable
  <A> A findSingleAttribute();

  /**
   * Execute the query returning a single optional attribute value.
   * <p>
   * <h3>Example</h3>
   * <pre>{@code
   *
   *  Optional<String> maybeName =
   *    new QCustomer()
   *      .select(name)
   *      .id.eq(42)
   *      .status.eq(NEW)
   *      .findSingleAttributeOrEmpty();
   *
   * }</pre>
   *
   * @return an optional value for the selected property
   */
  <A> Optional<A> findSingleAttributeOrEmpty();

  /**
   * Execute the query processing the beans one at a time.
   * <p>
   * This method is appropriate to process very large query results as the
   * beans are consumed one at a time and do not need to be held in memory
   * (unlike #findList #findSet etc)
   * <p>
   * Note that internally Ebean can inform the JDBC driver that it is expecting larger
   * resultSet and specifically for MySQL this hint is required to stop it's JDBC driver
   * from buffering the entire resultSet. As such, for smaller resultSets findList() is
   * generally preferable.
   * <p>
   * Compared with #findEachWhile this will always process all the beans where as
   * #findEachWhile provides a way to stop processing the query result early before
   * all the beans have been read.
   * <p>
   * This method is functionally equivalent to findIterate() but instead of using an
   * iterator uses the Consumer interface which is better suited to use with closures.
   *
   * <pre>{@code
   *
   *  new QCustomer()
   *     .status.equalTo(Status.NEW)
   *     .orderBy().id.asc()
   *     .findEach((Customer customer) -> {
   *
   *       // do something with customer
   *       System.out.println("-- visit " + customer);
   *     });
   *
   * }</pre>
   *
   * @param consumer the consumer used to process the queried beans.
   */
  void findEach(Consumer<T> consumer);

  /**
   * Execute findEach streaming query batching the results for consuming.
   * <p>
   * This query execution will stream the results and is suited to consuming
   * large numbers of results from the database.
   * <p>
   * Typically, we use this batch consumer when we want to do further processing on
   * the beans and want to do that processing in batch form, for example - 100 at
   * a time.
   *
   * @param batch    The number of beans processed in the batch
   * @param consumer Process the batch of beans
   */
  void findEach(int batch, Consumer<List<T>> consumer);

  /**
   * Execute the query using callbacks to a visitor to process the resulting
   * beans one at a time.
   * <p>
   * This method is functionally equivalent to findIterate() but instead of using an
   * iterator uses the Predicate interface which is better suited to use with closures.
   *
   * <pre>{@code
   *
   *  new QCustomer()
   *     .status.equalTo(Status.NEW)
   *     .orderBy().id.asc()
   *     .findEachWhile((Customer customer) -> {
   *
   *       // do something with customer
   *       System.out.println("-- visit " + customer);
   *
   *       // return true to continue processing or false to stop
   *       return (customer.getId() < 40);
   *     });
   *
   * }</pre>
   *
   * @param consumer the consumer used to process the queried beans.
   */
  void findEachWhile(Predicate<T> consumer);

  /**
   * Return versions of a @History entity bean.
   * <p>
   * Generally this query is expected to be a find by id or unique predicates query.
   * It will execute the query against the history returning the versions of the bean.
   */
  List<Version<T>> findVersions();

  /**
   * Return versions of a @History entity bean between a start and end timestamp.
   * <p>
   * Generally this query is expected to be a find by id or unique predicates query.
   * It will execute the query against the history returning the versions of the bean.
   */
  List<Version<T>> findVersionsBetween(Timestamp start, Timestamp end);

  /**
   * Return the count of entities this query should return.
   * <p>
   * This is the number of 'top level' or 'root level' entities.
   */
  int findCount();

  /**
   * Execute find row count query in a background thread.
   * <p>
   * This returns a Future object which can be used to cancel, check the
   * execution status (isDone etc) and get the value (with or without a
   * timeout).
   *
   * @return a Future object for the row count query
   */
  FutureRowCount<T> findFutureCount();

  /**
   * Execute find Id's query in a background thread.
   * <p>
   * This returns a Future object which can be used to cancel, check the
   * execution status (isDone etc) and get the value (with or without a
   * timeout).
   *
   * @return a Future object for the list of Id's
   */
  FutureIds<T> findFutureIds();

  /**
   * Execute find list query in a background thread.
   * <p>
   * This query will execute in it's own PersistenceContext and using its own transaction.
   * What that means is that it will not share any bean instances with other queries.
   *
   * @return a Future object for the list result of the query
   */
  FutureList<T> findFutureList();

  /**
   * Return a PagedList for this query using firstRow and maxRows.
   * <p>
   * The benefit of using this over findList() is that it provides functionality to get the
   * total row count etc.
   * <p>
   * If maxRows is not set on the query prior to calling findPagedList() then a
   * PersistenceException is thrown.
   * <p>
   * <pre>{@code
   *
   *  PagedList<Order> pagedList =
   *    new QOrder()
   *       .setFirstRow(50)
   *       .setMaxRows(20)
   *       .findPagedList();
   *
   *       // fetch the total row count in the background
   *       pagedList.loadRowCount();
   *
   *       List<Order> orders = pagedList.getList();
   *       int totalRowCount = pagedList.getTotalRowCount();
   *
   * }</pre>
   *
   * @return The PagedList
   */
  PagedList<T> findPagedList();

  /**
   * Execute as a delete query deleting the 'root level' beans that match the predicates
   * in the query.
   * <p>
   * Note that if the query includes joins then the generated delete statement may not be
   * optimal depending on the database platform.
   *
   * @return the number of beans/rows that were deleted.
   */
  int delete();

  /**
   * Return the sql that was generated for executing this query.
   * <p>
   * This is only available after the query has been executed and provided only
   * for informational purposes.
   */
  String getGeneratedSql();

  /**
   * Return the type of beans being queried.
   */
  Class<T> getBeanType();

  /**
   * Return the expression list that has been built for this query.
   */
  ExpressionList<T> getExpressionList();

  /**
   * Start adding expressions to the having clause when using @Aggregation properties.
   *
   * <pre>{@code
   *
   *   new QMachineUse()
   *   // where ...
   *   .date.inRange(fromDate, toDate)
   *
   *   .having()
   *   .sumHours.greaterThan(1)
   *   .findList()
   *
   *   // The sumHours property uses @Aggregation
   *   // e.g. @Aggregation("sum(hours)")
   *
   * }</pre>
   */
  R having();

  /**
   * Return the underlying having clause to typically when using dynamic aggregation formula.
   * <p>
   * Note that after this we no longer have the query bean so typically we use this right
   * at the end of the query.
   *
   * <pre>{@code
   *
   *  // sum(distanceKms) ... is a "dynamic formula"
   *  // so we use havingClause() for it like:
   *
   *  List<MachineUse> machineUse =
   *
   *    new QMachineUse()
   *      .select("machine, sum(fuelUsed), sum(distanceKms)")
   *
   *      // where ...
   *      .date.greaterThan(LocalDate.now().minusDays(7))
   *
   *      .havingClause()
   *        .gt("sum(distanceKms)", 2)
   *        .findList();
   *
   * }</pre>
   */
  ExpressionList<T> havingClause();
}