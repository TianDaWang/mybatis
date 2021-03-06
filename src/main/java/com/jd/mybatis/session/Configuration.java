/**
 * Copyright 2009-2015 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jd.mybatis.session;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.jd.mybatis.binding.MapperRegistry;
import com.jd.mybatis.builder.ResultMapResolver;
import com.jd.mybatis.builder.annotation.MethodResolver;
import com.jd.mybatis.builder.xml.XMLStatementBuilder;
import com.jd.mybatis.cache.decorators.SoftCache;
import com.jd.mybatis.cache.decorators.WeakCache;
import com.jd.mybatis.datasource.jndi.JndiDataSourceFactory;
import com.jd.mybatis.datasource.pooled.PooledDataSourceFactory;
import com.jd.mybatis.datasource.unpooled.UnpooledDataSourceFactory;
import com.jd.mybatis.executor.BatchExecutor;
import com.jd.mybatis.executor.CachingExecutor;
import com.jd.mybatis.executor.Executor;
import com.jd.mybatis.executor.keygen.KeyGenerator;
import com.jd.mybatis.executor.loader.ProxyFactory;
import com.jd.mybatis.executor.loader.javassist.JavassistProxyFactory;
import com.jd.mybatis.executor.parameter.ParameterHandler;
import com.jd.mybatis.executor.resultset.DefaultResultSetHandler;
import com.jd.mybatis.executor.statement.RoutingStatementHandler;
import com.jd.mybatis.executor.statement.StatementHandler;
import com.jd.mybatis.logging.Log;
import com.jd.mybatis.logging.LogFactory;
import com.jd.mybatis.logging.commons.JakartaCommonsLoggingImpl;
import com.jd.mybatis.logging.jdk14.Jdk14LoggingImpl;
import com.jd.mybatis.logging.log4j.Log4jImpl;
import com.jd.mybatis.logging.nologging.NoLoggingImpl;
import com.jd.mybatis.logging.slf4j.Slf4jImpl;
import com.jd.mybatis.parsing.XNode;
import com.jd.mybatis.plugin.Interceptor;
import com.jd.mybatis.plugin.InterceptorChain;
import com.jd.mybatis.reflection.MetaObject;
import com.jd.mybatis.reflection.ReflectorFactory;
import com.jd.mybatis.reflection.factory.DefaultObjectFactory;
import com.jd.mybatis.reflection.factory.ObjectFactory;
import com.jd.mybatis.transaction.Transaction;
import com.jd.mybatis.type.JdbcType;
import com.jd.mybatis.type.TypeHandlerRegistry;
import com.jd.mybatis.builder.CacheRefResolver;
import com.jd.mybatis.cache.Cache;
import com.jd.mybatis.cache.decorators.FifoCache;
import com.jd.mybatis.cache.decorators.LruCache;
import com.jd.mybatis.cache.impl.PerpetualCache;
import com.jd.mybatis.executor.ReuseExecutor;
import com.jd.mybatis.executor.SimpleExecutor;
import com.jd.mybatis.executor.loader.cglib.CglibProxyFactory;
import com.jd.mybatis.executor.resultset.ResultSetHandler;
import com.jd.mybatis.logging.log4j2.Log4j2Impl;
import com.jd.mybatis.logging.stdout.StdOutImpl;
import com.jd.mybatis.mapping.BoundSql;
import com.jd.mybatis.mapping.Environment;
import com.jd.mybatis.mapping.MappedStatement;
import com.jd.mybatis.mapping.ParameterMap;
import com.jd.mybatis.mapping.ResultMap;
import com.jd.mybatis.mapping.VendorDatabaseIdProvider;
import com.jd.mybatis.reflection.DefaultReflectorFactory;
import com.jd.mybatis.reflection.wrapper.DefaultObjectWrapperFactory;
import com.jd.mybatis.reflection.wrapper.ObjectWrapperFactory;
import com.jd.mybatis.scripting.LanguageDriver;
import com.jd.mybatis.scripting.LanguageDriverRegistry;
import com.jd.mybatis.scripting.defaults.RawLanguageDriver;
import com.jd.mybatis.scripting.xmltags.XMLLanguageDriver;
import com.jd.mybatis.transaction.jdbc.JdbcTransactionFactory;
import com.jd.mybatis.transaction.managed.ManagedTransactionFactory;
import com.jd.mybatis.type.TypeAliasRegistry;

/**
 * 所有的配置信息都维持在Configuration对象之中
 * @author Clinton Begin
 */
public class Configuration {

	protected Environment environment;

	protected boolean safeRowBoundsEnabled = false;
	protected boolean safeResultHandlerEnabled = true;
	protected boolean mapUnderscoreToCamelCase = false;
	//当设置为‘true’的时候，懒加载的对象可能被任何懒属性全部加载。否则，每个属性都按需加载。
	protected boolean aggressiveLazyLoading = true;
	//允许和不允许单条语句返回多个数据集（取决于驱动需求）
	protected boolean multipleResultSetsEnabled = true;
	//允许JDBC 生成主键。需要驱动器支持。如果设为了true，这个设置将强制使用被生成的主键，有一些驱动器不兼容不过仍然可以执行。
	protected boolean useGeneratedKeys = false;
	//使用列标签代替列名称。不同的驱动器有不同的作法。参考一下驱动器文档，或者用这两个不同的选项进行测试一下。
	protected boolean useColumnLabel = true;
	//对在此配置文件下的所有cache 进行全局性开/关设置。
	protected boolean cacheEnabled = true;
	protected boolean callSettersOnNulls = false;

	protected String logPrefix;
	protected Class<? extends Log> logImpl;
	protected LocalCacheScope localCacheScope = LocalCacheScope.SESSION;
	protected JdbcType jdbcTypeForNull = JdbcType.OTHER;
	protected Set<String> lazyLoadTriggerMethods = new HashSet<>(Arrays.asList(new String[]{"equals", "clone", "hashCode", "toString"}));
	//设置一个时限，以决定让驱动器等待数据库回应的多长时间为超时
	protected Integer defaultStatementTimeout;
	protected Integer defaultFetchSize;
	//配置和设定执行器，SIMPLE 执行器执行其它语句。REUSE 执行器可能重复使用prepared statements 语句，BATCH执行器可以重复执行语句和批量更新。
	protected ExecutorType defaultExecutorType = ExecutorType.SIMPLE;
	//指定MyBatis 是否并且如何来自动映射数据表字段与对象的属性。PARTIAL将只自动映射简单的，没有嵌套的结果。FULL 将自动映射所有复杂的结果。
	protected AutoMappingBehavior autoMappingBehavior = AutoMappingBehavior.PARTIAL;

	protected Properties variables = new Properties();
	protected ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
	protected ObjectFactory objectFactory = new DefaultObjectFactory();
	protected ObjectWrapperFactory objectWrapperFactory = new DefaultObjectWrapperFactory();
	protected MapperRegistry mapperRegistry = new MapperRegistry(this);

	//全局性设置懒加载。如果设为‘false’，则所有相关联的都会被初始化加载。
	protected boolean lazyLoadingEnabled = false;
	protected ProxyFactory proxyFactory = new JavassistProxyFactory(); // #224 Using internal Javassist instead of OGNL

	protected String databaseId;
	/**
	 * Configuration factory class.
	 * Used to create Configuration for loading deserialized unread properties.
	 *
	 * @see <a href='https://code.google.com/p/mybatis/issues/detail?id=300'>Issue 300</a> (google code)
	 */
	protected Class<?> configurationFactory;

	protected final InterceptorChain interceptorChain = new InterceptorChain();
	protected final TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry();
	protected final TypeAliasRegistry typeAliasRegistry = new TypeAliasRegistry();
	protected final LanguageDriverRegistry languageRegistry = new LanguageDriverRegistry();

	protected final Map<String, MappedStatement> mappedStatements = new StrictMap<>("Mapped Statements collection");
	protected final Map<String, Cache> caches = new StrictMap<>("Caches collection");
	protected final Map<String, ResultMap> resultMaps = new StrictMap<>("Result Maps collection");
	protected final Map<String, ParameterMap> parameterMaps = new StrictMap<>("Parameter Maps collection");
	protected final Map<String, KeyGenerator> keyGenerators = new StrictMap<>("Key Generators collection");

	protected final Set<String> loadedResources = new HashSet<>();
	protected final Map<String, XNode> sqlFragments = new StrictMap<>("XML fragments parsed from previous mappers");

	protected final Collection<XMLStatementBuilder> incompleteStatements = new LinkedList<>();
	/**
	 * 在程序初始化时，可能有一些参照缓存尚未被解析完成，但是解析Mapper时用到了，所以先把这些情况给记录下来
	 */
	protected final Collection<CacheRefResolver> incompleteCacheRefs = new LinkedList<>();
	protected final Collection<ResultMapResolver> incompleteResultMaps = new LinkedList<>();
	protected final Collection<MethodResolver> incompleteMethods = new LinkedList<>();

	/*
	 * A map holds cache-ref relationship. The key is the namespace that
	 * references a cache bound to another namespace and the value is the
	 * namespace which the actual cache is bound to.
	 */
	protected final Map<String, String> cacheRefMap = new HashMap<String, String>();

	public Configuration(Environment environment) {
		this();
		this.environment = environment;
	}

	/**
	 * 构造函数
	 * 注册默认别名类型
	 */
	public Configuration() {
		//事务类型
		typeAliasRegistry.registerAlias("JDBC", JdbcTransactionFactory.class);
		typeAliasRegistry.registerAlias("MANAGED", ManagedTransactionFactory.class);
		//数据源与连接池类型
		typeAliasRegistry.registerAlias("JNDI", JndiDataSourceFactory.class);
		typeAliasRegistry.registerAlias("POOLED", PooledDataSourceFactory.class);
		typeAliasRegistry.registerAlias("UNPOOLED", UnpooledDataSourceFactory.class);

		typeAliasRegistry.registerAlias("PERPETUAL", PerpetualCache.class);
		typeAliasRegistry.registerAlias("FIFO", FifoCache.class);
		typeAliasRegistry.registerAlias("LRU", LruCache.class);
		typeAliasRegistry.registerAlias("SOFT", SoftCache.class);
		typeAliasRegistry.registerAlias("WEAK", WeakCache.class);

		typeAliasRegistry.registerAlias("DB_VENDOR", VendorDatabaseIdProvider.class);

		typeAliasRegistry.registerAlias("XML", XMLLanguageDriver.class);
		typeAliasRegistry.registerAlias("RAW", RawLanguageDriver.class);

		typeAliasRegistry.registerAlias("SLF4J", Slf4jImpl.class);
		typeAliasRegistry.registerAlias("COMMONS_LOGGING", JakartaCommonsLoggingImpl.class);
		typeAliasRegistry.registerAlias("LOG4J", Log4jImpl.class);
		typeAliasRegistry.registerAlias("LOG4J2", Log4j2Impl.class);
		typeAliasRegistry.registerAlias("JDK_LOGGING", Jdk14LoggingImpl.class);
		typeAliasRegistry.registerAlias("STDOUT_LOGGING", StdOutImpl.class);
		typeAliasRegistry.registerAlias("NO_LOGGING", NoLoggingImpl.class);

		typeAliasRegistry.registerAlias("CGLIB", CglibProxyFactory.class);
		typeAliasRegistry.registerAlias("JAVASSIST", JavassistProxyFactory.class);

		languageRegistry.setDefaultDriverClass(XMLLanguageDriver.class);
		languageRegistry.register(RawLanguageDriver.class);
	}

	public String getLogPrefix() {
		return logPrefix;
	}

	public void setLogPrefix(String logPrefix) {
		this.logPrefix = logPrefix;
	}

	public Class<? extends Log> getLogImpl() {
		return logImpl;
	}

	@SuppressWarnings("unchecked")
	public void setLogImpl(Class<?> logImpl) {
		if (logImpl != null) {
			this.logImpl = (Class<? extends Log>) logImpl;
			LogFactory.useCustomLogging(this.logImpl);
		}
	}

	public boolean isCallSettersOnNulls() {
		return callSettersOnNulls;
	}

	public void setCallSettersOnNulls(boolean callSettersOnNulls) {
		this.callSettersOnNulls = callSettersOnNulls;
	}

	public String getDatabaseId() {
		return databaseId;
	}

	public void setDatabaseId(String databaseId) {
		this.databaseId = databaseId;
	}

	public Class<?> getConfigurationFactory() {
		return configurationFactory;
	}

	public void setConfigurationFactory(Class<?> configurationFactory) {
		this.configurationFactory = configurationFactory;
	}

	public boolean isSafeResultHandlerEnabled() {
		return safeResultHandlerEnabled;
	}

	public void setSafeResultHandlerEnabled(boolean safeResultHandlerEnabled) {
		this.safeResultHandlerEnabled = safeResultHandlerEnabled;
	}

	public boolean isSafeRowBoundsEnabled() {
		return safeRowBoundsEnabled;
	}

	public void setSafeRowBoundsEnabled(boolean safeRowBoundsEnabled) {
		this.safeRowBoundsEnabled = safeRowBoundsEnabled;
	}

	public boolean isMapUnderscoreToCamelCase() {
		return mapUnderscoreToCamelCase;
	}

	public void setMapUnderscoreToCamelCase(boolean mapUnderscoreToCamelCase) {
		this.mapUnderscoreToCamelCase = mapUnderscoreToCamelCase;
	}

	public void addLoadedResource(String resource) {
		loadedResources.add(resource);
	}

	public boolean isResourceLoaded(String resource) {
		return loadedResources.contains(resource);
	}

	public Environment getEnvironment() {
		return environment;
	}

	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	public AutoMappingBehavior getAutoMappingBehavior() {
		return autoMappingBehavior;
	}

	public void setAutoMappingBehavior(AutoMappingBehavior autoMappingBehavior) {
		this.autoMappingBehavior = autoMappingBehavior;
	}

	public boolean isLazyLoadingEnabled() {
		return lazyLoadingEnabled;
	}

	public void setLazyLoadingEnabled(boolean lazyLoadingEnabled) {
		this.lazyLoadingEnabled = lazyLoadingEnabled;
	}

	public ProxyFactory getProxyFactory() {
		return proxyFactory;
	}

	public void setProxyFactory(ProxyFactory proxyFactory) {
		if (proxyFactory == null) {
			proxyFactory = new JavassistProxyFactory();
		}
		this.proxyFactory = proxyFactory;
	}

	public boolean isAggressiveLazyLoading() {
		return aggressiveLazyLoading;
	}

	public void setAggressiveLazyLoading(boolean aggressiveLazyLoading) {
		this.aggressiveLazyLoading = aggressiveLazyLoading;
	}

	public boolean isMultipleResultSetsEnabled() {
		return multipleResultSetsEnabled;
	}

	public void setMultipleResultSetsEnabled(boolean multipleResultSetsEnabled) {
		this.multipleResultSetsEnabled = multipleResultSetsEnabled;
	}

	public Set<String> getLazyLoadTriggerMethods() {
		return lazyLoadTriggerMethods;
	}

	public void setLazyLoadTriggerMethods(Set<String> lazyLoadTriggerMethods) {
		this.lazyLoadTriggerMethods = lazyLoadTriggerMethods;
	}

	public boolean isUseGeneratedKeys() {
		return useGeneratedKeys;
	}

	public void setUseGeneratedKeys(boolean useGeneratedKeys) {
		this.useGeneratedKeys = useGeneratedKeys;
	}

	public ExecutorType getDefaultExecutorType() {
		return defaultExecutorType;
	}

	public void setDefaultExecutorType(ExecutorType defaultExecutorType) {
		this.defaultExecutorType = defaultExecutorType;
	}

	public boolean isCacheEnabled() {
		return cacheEnabled;
	}

	public void setCacheEnabled(boolean cacheEnabled) {
		this.cacheEnabled = cacheEnabled;
	}

	public Integer getDefaultStatementTimeout() {
		return defaultStatementTimeout;
	}

	public void setDefaultStatementTimeout(Integer defaultStatementTimeout) {
		this.defaultStatementTimeout = defaultStatementTimeout;
	}

	public Integer getDefaultFetchSize() {
		return defaultFetchSize;
	}

	public void setDefaultFetchSize(Integer defaultFetchSize) {
		this.defaultFetchSize = defaultFetchSize;
	}

	public boolean isUseColumnLabel() {
		return useColumnLabel;
	}

	public void setUseColumnLabel(boolean useColumnLabel) {
		this.useColumnLabel = useColumnLabel;
	}

	public LocalCacheScope getLocalCacheScope() {
		return localCacheScope;
	}

	public void setLocalCacheScope(LocalCacheScope localCacheScope) {
		this.localCacheScope = localCacheScope;
	}

	public JdbcType getJdbcTypeForNull() {
		return jdbcTypeForNull;
	}

	public void setJdbcTypeForNull(JdbcType jdbcTypeForNull) {
		this.jdbcTypeForNull = jdbcTypeForNull;
	}

	public Properties getVariables() {
		return variables;
	}

	public void setVariables(Properties variables) {
		this.variables = variables;
	}

	public TypeHandlerRegistry getTypeHandlerRegistry() {
		return typeHandlerRegistry;
	}

	public TypeAliasRegistry getTypeAliasRegistry() {
		return typeAliasRegistry;
	}

	/**
	 * @since 3.2.2
	 */
	public MapperRegistry getMapperRegistry() {
		return mapperRegistry;
	}

	public ReflectorFactory getReflectorFactory() {
		return reflectorFactory;
	}

	public void setReflectorFactory(ReflectorFactory reflectorFactory) {
		this.reflectorFactory = reflectorFactory;
	}

	public ObjectFactory getObjectFactory() {
		return objectFactory;
	}

	public void setObjectFactory(ObjectFactory objectFactory) {
		this.objectFactory = objectFactory;
	}

	public ObjectWrapperFactory getObjectWrapperFactory() {
		return objectWrapperFactory;
	}

	public void setObjectWrapperFactory(ObjectWrapperFactory objectWrapperFactory) {
		this.objectWrapperFactory = objectWrapperFactory;
	}

	/**
	 * @since 3.2.2
	 */
	public List<Interceptor> getInterceptors() {
		return interceptorChain.getInterceptors();
	}

	public LanguageDriverRegistry getLanguageRegistry() {
		return languageRegistry;
	}

	public void setDefaultScriptingLanguage(Class<?> driver) {
		if (driver == null) {
			driver = XMLLanguageDriver.class;
		}
		getLanguageRegistry().setDefaultDriverClass(driver);
	}

	public LanguageDriver getDefaultScriptingLanuageInstance() {
		return languageRegistry.getDefaultDriver();
	}

	public MetaObject newMetaObject(Object object) {
		return MetaObject.forObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
	}

	public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
		ParameterHandler parameterHandler = mappedStatement.getLang().createParameterHandler(mappedStatement, parameterObject, boundSql);
		parameterHandler = (ParameterHandler) interceptorChain.pluginAll(parameterHandler);
		return parameterHandler;
	}

	public ResultSetHandler newResultSetHandler(Executor executor, MappedStatement mappedStatement, RowBounds rowBounds, ParameterHandler parameterHandler,
												ResultHandler resultHandler, BoundSql boundSql) {
		ResultSetHandler resultSetHandler = new DefaultResultSetHandler(executor, mappedStatement, parameterHandler, resultHandler, boundSql, rowBounds);
		resultSetHandler = (ResultSetHandler) interceptorChain.pluginAll(resultSetHandler);
		return resultSetHandler;
	}

	public StatementHandler newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
		StatementHandler statementHandler = new RoutingStatementHandler(executor, mappedStatement, parameterObject, rowBounds, resultHandler, boundSql);
		statementHandler = (StatementHandler) interceptorChain.pluginAll(statementHandler);
		return statementHandler;
	}

	public Executor newExecutor(Transaction transaction) {
		return newExecutor(transaction, defaultExecutorType);
	}

	public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
		executorType = executorType == null ? defaultExecutorType : executorType;
		executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
		Executor executor;
		if (ExecutorType.BATCH == executorType) {
			executor = new BatchExecutor(this, transaction);
		} else if (ExecutorType.REUSE == executorType) {
			executor = new ReuseExecutor(this, transaction);
		} else {
			executor = new SimpleExecutor(this, transaction);
		}
	/*一个SqlSession对象会使用一个Executor对象来完成会话操作，MyBatis的二级缓存机制的关键就是对这个Executor对象做文章。
     * 如果用户配置了"cacheEnabled=true"，那么MyBatis在为SqlSession对象创建Executor对象时，
     * 会对Executor对象加上一个装饰者：CachingExecutor，这时SqlSession使用CachingExecutor对象来完成操作请求。
     * CachingExecutor对于查询请求，会先判断该查询请求在Application级别的二级缓存中是否有缓存结果，如果有查询结果，
     * 则直接返回缓存结果；如果缓存中没有，再交给真正的Executor对象来完成查询操作，
     * 之后CachingExecutor会将真正Executor返回的查询结果放置到缓存中，然后在返回给用户。
     */
		if (cacheEnabled) {
			executor = new CachingExecutor(executor);
		}
		executor = (Executor) interceptorChain.pluginAll(executor);
		return executor;
	}

	public void addKeyGenerator(String id, KeyGenerator keyGenerator) {
		keyGenerators.put(id, keyGenerator);
	}

	public Collection<String> getKeyGeneratorNames() {
		return keyGenerators.keySet();
	}

	public Collection<KeyGenerator> getKeyGenerators() {
		return keyGenerators.values();
	}

	public KeyGenerator getKeyGenerator(String id) {
		return keyGenerators.get(id);
	}

	public boolean hasKeyGenerator(String id) {
		return keyGenerators.containsKey(id);
	}

	/**
	 * 添加缓存，将命名空间与缓存进行绑定
	 * @param cache
	 */
	public void addCache(Cache cache) {
		caches.put(cache.getId(), cache);
	}

	public Collection<String> getCacheNames() {
		return caches.keySet();
	}

	public Collection<Cache> getCaches() {
		return caches.values();
	}

	public Cache getCache(String id) {
		return caches.get(id);
	}

	public boolean hasCache(String id) {
		return caches.containsKey(id);
	}

	public void addResultMap(ResultMap rm) {
		resultMaps.put(rm.getId(), rm);
		checkLocallyForDiscriminatedNestedResultMaps(rm);
		checkGloballyForDiscriminatedNestedResultMaps(rm);
	}

	public Collection<String> getResultMapNames() {
		return resultMaps.keySet();
	}

	public Collection<ResultMap> getResultMaps() {
		return resultMaps.values();
	}

	public ResultMap getResultMap(String id) {
		return resultMaps.get(id);
	}

	public boolean hasResultMap(String id) {
		return resultMaps.containsKey(id);
	}

	public void addParameterMap(ParameterMap pm) {
		parameterMaps.put(pm.getId(), pm);
	}

	public Collection<String> getParameterMapNames() {
		return parameterMaps.keySet();
	}

	public Collection<ParameterMap> getParameterMaps() {
		return parameterMaps.values();
	}

	public ParameterMap getParameterMap(String id) {
		return parameterMaps.get(id);
	}

	public boolean hasParameterMap(String id) {
		return parameterMaps.containsKey(id);
	}

	public void addMappedStatement(MappedStatement ms) {
		mappedStatements.put(ms.getId(), ms);
	}

	public Collection<String> getMappedStatementNames() {
		buildAllStatements();
		return mappedStatements.keySet();
	}

	public Collection<MappedStatement> getMappedStatements() {
		buildAllStatements();
		return mappedStatements.values();
	}

	public Collection<XMLStatementBuilder> getIncompleteStatements() {
		return incompleteStatements;
	}

	public void addIncompleteStatement(XMLStatementBuilder incompleteStatement) {
		incompleteStatements.add(incompleteStatement);
	}

	public Collection<CacheRefResolver> getIncompleteCacheRefs() {
		return incompleteCacheRefs;
	}

	/**
	 * 添加未初始化的参照缓存
	 * @param incompleteCacheRef	参照缓存的解析器
	 */
	public void addIncompleteCacheRef(CacheRefResolver incompleteCacheRef) {
		incompleteCacheRefs.add(incompleteCacheRef);
	}

	public Collection<ResultMapResolver> getIncompleteResultMaps() {
		return incompleteResultMaps;
	}

	public void addIncompleteResultMap(ResultMapResolver resultMapResolver) {
		incompleteResultMaps.add(resultMapResolver);
	}

	public void addIncompleteMethod(MethodResolver builder) {
		incompleteMethods.add(builder);
	}

	public Collection<MethodResolver> getIncompleteMethods() {
		return incompleteMethods;
	}

	public MappedStatement getMappedStatement(String id) {
		return this.getMappedStatement(id, true);
	}

	public MappedStatement getMappedStatement(String id, boolean validateIncompleteStatements) {
		if (validateIncompleteStatements) {
			buildAllStatements();
		}
		return mappedStatements.get(id);
	}

	public Map<String, XNode> getSqlFragments() {
		return sqlFragments;
	}

	public void addInterceptor(Interceptor interceptor) {
		interceptorChain.addInterceptor(interceptor);
	}

	public void addMappers(String packageName, Class<?> superType) {
		mapperRegistry.addMappers(packageName, superType);
	}

	public void addMappers(String packageName) {
		mapperRegistry.addMappers(packageName);
	}

	public <T> void addMapper(Class<T> type) {
		mapperRegistry.addMapper(type);
	}

	public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
		return mapperRegistry.getMapper(type, sqlSession);
	}

	public boolean hasMapper(Class<?> type) {
		return mapperRegistry.hasMapper(type);
	}

	public boolean hasStatement(String statementName) {
		return hasStatement(statementName, true);
	}

	public boolean hasStatement(String statementName, boolean validateIncompleteStatements) {
		if (validateIncompleteStatements) {
			buildAllStatements();
		}
		return mappedStatements.containsKey(statementName);
	}

	public void addCacheRef(String namespace, String referencedNamespace) {
		cacheRefMap.put(namespace, referencedNamespace);
	}

	/*
	 * Parses all the unprocessed statement nodes in the cache. It is recommended
	 * to call this method once all the mappers are added as it provides fail-fast
	 * statement validation.
	 */
	protected void buildAllStatements() {
		if (!incompleteResultMaps.isEmpty()) {
			synchronized (incompleteResultMaps) {
				// This always throws a BuilderException.
				incompleteResultMaps.iterator().next().resolve();
			}
		}
		if (!incompleteCacheRefs.isEmpty()) {
			synchronized (incompleteCacheRefs) {
				// This always throws a BuilderException.
				incompleteCacheRefs.iterator().next().resolveCacheRef();
			}
		}
		if (!incompleteStatements.isEmpty()) {
			synchronized (incompleteStatements) {
				// This always throws a BuilderException.
				incompleteStatements.iterator().next().parseStatementNode();
			}
		}
		if (!incompleteMethods.isEmpty()) {
			synchronized (incompleteMethods) {
				// This always throws a BuilderException.
				incompleteMethods.iterator().next().resolve();
			}
		}
	}

	/*
	 * Extracts namespace from fully qualified statement id.
	 *
	 * @param statementId
	 * @return namespace or null when id does not contain period.
	 */
	protected String extractNamespace(String statementId) {
		int lastPeriod = statementId.lastIndexOf('.');
		return lastPeriod > 0 ? statementId.substring(0, lastPeriod) : null;
	}

	// Slow but a one time cost. A better solution is welcome.
	protected void checkGloballyForDiscriminatedNestedResultMaps(ResultMap rm) {
		if (rm.hasNestedResultMaps()) {
			for (Map.Entry<String, ResultMap> entry : resultMaps.entrySet()) {
				Object value = entry.getValue();
				if (value instanceof ResultMap) {
					ResultMap entryResultMap = (ResultMap) value;
					if (!entryResultMap.hasNestedResultMaps() && entryResultMap.getDiscriminator() != null) {
						Collection<String> discriminatedResultMapNames = entryResultMap.getDiscriminator().getDiscriminatorMap().values();
						if (discriminatedResultMapNames.contains(rm.getId())) {
							entryResultMap.forceNestedResultMaps();
						}
					}
				}
			}
		}
	}

	// Slow but a one time cost. A better solution is welcome.
	protected void checkLocallyForDiscriminatedNestedResultMaps(ResultMap rm) {
		if (!rm.hasNestedResultMaps() && rm.getDiscriminator() != null) {
			for (Map.Entry<String, String> entry : rm.getDiscriminator().getDiscriminatorMap().entrySet()) {
				String discriminatedResultMapName = entry.getValue();
				if (hasResultMap(discriminatedResultMapName)) {
					ResultMap discriminatedResultMap = resultMaps.get(discriminatedResultMapName);
					if (discriminatedResultMap.hasNestedResultMaps()) {
						rm.forceNestedResultMaps();
						break;
					}
				}
			}
		}
	}

	protected static class StrictMap<V> extends HashMap<String, V> {

		private static final long serialVersionUID = -4950446264854982944L;
		private String name;

		public StrictMap(String name, int initialCapacity, float loadFactor) {
			super(initialCapacity, loadFactor);
			this.name = name;
		}

		public StrictMap(String name, int initialCapacity) {
			super(initialCapacity);
			this.name = name;
		}

		public StrictMap(String name) {
			super();
			this.name = name;
		}

		public StrictMap(String name, Map<String, ? extends V> m) {
			super(m);
			this.name = name;
		}

		@SuppressWarnings("unchecked")
		public V put(String key, V value) {
			if (containsKey(key)) {
				throw new IllegalArgumentException(name + " already contains value for " + key);
			}
			if (key.contains(".")) {
				final String shortKey = getShortName(key);
				if (super.get(shortKey) == null) {
					super.put(shortKey, value);
				} else {
					super.put(shortKey, (V) new Ambiguity(shortKey));
				}
			}
			return super.put(key, value);
		}

		public V get(Object key) {
			V value = super.get(key);
			if (value == null) {
				throw new IllegalArgumentException(name + " does not contain value for " + key);
			}
			if (value instanceof Ambiguity) {
				throw new IllegalArgumentException(((Ambiguity) value).getSubject() + " is ambiguous in " + name
						+ " (try using the full name including the namespace, or rename one of the entries)");
			}
			return value;
		}

		private String getShortName(String key) {
			final String[] keyparts = key.split("\\.");
			return keyparts[keyparts.length - 1];
		}

		protected static class Ambiguity {
			private String subject;

			public Ambiguity(String subject) {
				this.subject = subject;
			}

			public String getSubject() {
				return subject;
			}
		}
	}

}
