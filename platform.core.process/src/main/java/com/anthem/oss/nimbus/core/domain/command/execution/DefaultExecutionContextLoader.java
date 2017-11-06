/**
 * 
 */
package com.anthem.oss.nimbus.core.domain.command.execution;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.anthem.oss.nimbus.core.BeanResolverStrategy;
import com.anthem.oss.nimbus.core.domain.command.Action;
import com.anthem.oss.nimbus.core.domain.command.Behavior;
import com.anthem.oss.nimbus.core.domain.command.Command;
import com.anthem.oss.nimbus.core.domain.command.CommandMessage;
import com.anthem.oss.nimbus.core.domain.command.execution.CommandExecution.Input;
import com.anthem.oss.nimbus.core.domain.command.execution.CommandExecution.Output;
import com.anthem.oss.nimbus.core.domain.config.builder.DomainConfigBuilder;
import com.anthem.oss.nimbus.core.domain.definition.Repo;
import com.anthem.oss.nimbus.core.domain.model.config.ModelConfig;
import com.anthem.oss.nimbus.core.domain.model.state.QuadModel;
import com.anthem.oss.nimbus.core.domain.model.state.builder.QuadModelBuilder;
import com.anthem.oss.nimbus.core.util.JustLogit;

/**
 * @author Soham Chakravarti
 *
 */
public class DefaultExecutionContextLoader implements ExecutionContextLoader {

	private final DomainConfigBuilder domainConfigBuilder;
	private final CommandExecutor<?> executorActionNew;
	private final CommandExecutor<?> executorActionGet;

	// TODO: Temp impl till Session is rolled out
	private final Map<String, ExecutionContext> sessionCache;
	
	private final QuadModelBuilder quadModelBuilder;
	
	private final SessionProvider sessionProvider;
	
	private static final JustLogit logit = new JustLogit(DefaultExecutionContextLoader.class);
	
	public DefaultExecutionContextLoader(BeanResolverStrategy beanResolver) {
		this.domainConfigBuilder = beanResolver.get(DomainConfigBuilder.class);
		this.quadModelBuilder = beanResolver.get(QuadModelBuilder.class);
		
		this.executorActionNew = beanResolver.get(CommandExecutor.class, Action._new.name() + Behavior.$execute.name());
		this.executorActionGet = beanResolver.get(CommandExecutor.class, Action._get.name() + Behavior.$execute.name());
		
		this.sessionProvider = beanResolver.get(SessionProvider.class);
		
		// TODO: Temp impl till Session is rolled out
		this.sessionCache = new HashMap<>(100);
	}
	
//	private static String getSessionIdForLogging() {
//		final String thSessionId = TH_SESSION.get();
//		try {
//			String msg = "Session from HTTP: "+ RequestContextHolder.getRequestAttributes().getSessionId()+
//							" :: Session  from TH_SESSION: "+ thSessionId;
//			return msg;
//		} catch (Exception ex) {
//			logit.error(()->"Failed to get session info, TH_SESSION: "+thSessionId, ex);
//			return "Failed to get session from HTTP, TH_SESSION: "+thSessionId;
//		}
//	}
	
	@Override
	public ExecutionContext load(Command rootDomainCmd) {
		String sessionId = sessionProvider.getSessionId();
		return load(rootDomainCmd, sessionId);
	}
	@Override
	public final ExecutionContext load(Command rootDomainCmd, String sessionId) {
		logit.trace(()->"[load][I] rootDomainCmd:"+rootDomainCmd+" for "+sessionId);
		
		ExecutionContext eCtx = new ExecutionContext(rootDomainCmd);
		
		// _search: transient - just create shell 
		if(isTransient(rootDomainCmd)) {
			logit.trace(()->"[load] isTransient");
			
			QuadModel<?, ?> q = quadModelBuilder.build(rootDomainCmd);
			eCtx.setQuadModel(q);
			
		} else // _new takes priority
		if(rootDomainCmd.isRootDomainOnly() && rootDomainCmd.getAction()==Action._new) {
			logit.trace(()->"[load] isRootDomainOnly && _new");
			
			eCtx = loadEntity(eCtx, executorActionNew, sessionId);
			
		} else // check if already exists in session
		if(sessionExists(eCtx, sessionId)) { 
			logit.trace(()->"[load] sessionExists");
			
			QuadModel<?, ?> q = sessionGet(eCtx, sessionId);
			eCtx.setQuadModel(q);
			
		} else { // all else requires resurrecting entity
			logit.trace(()->"[load] do _get and put in sessionIfApplicable");
			
			eCtx = loadEntity(eCtx, executorActionGet, sessionId);
		}
		
		logit.trace(()->"[load][O] rootDomainCmd:"+rootDomainCmd+" for "+sessionId);
		return eCtx;
	}
	
	@Override
	public final void unload(ExecutionContext eCtx, String sessionId) {
		sessionRemomve(eCtx, sessionId);
		
		// also do an explicit shutdown
		eCtx.getQuadModel().getRoot().getExecutionRuntime().stop();
	}

	private boolean isTransient(Command cmd) {
		return cmd.getAction()==Action._search 
				|| cmd.getAction()==Action._config;
	}
	
	private ExecutionContext loadEntity(ExecutionContext eCtx, CommandExecutor<?> executor, String sessionId) {
		CommandMessage cmdMsg = eCtx.getCommandMessage();
		String inputCmdUri = cmdMsg.getCommand().getAbsoluteUri();
		
		Input input = new Input(inputCmdUri, eCtx, cmdMsg.getCommand().getAction(), Behavior.$execute);
		Output<?> output = executor.execute(input);
		
		// update context
		eCtx = output.getContext();
		
		ModelConfig<?> rootDomainConfig = domainConfigBuilder.getRootDomainOrThrowEx(cmdMsg.getCommand().getRootDomainAlias());
		
		sessionPutIfApplicable(rootDomainConfig, eCtx, sessionId);
		
		return eCtx;
	}
	
	protected boolean sessionPutIfApplicable(ModelConfig<?> rootDomainConfig, ExecutionContext eCtx, String sessionId) {
		Repo repo = rootDomainConfig.getRepo();
		if(repo==null)
			return false;
		
		if(repo.cache()==Repo.Cache.rep_device) {
			return queuePut(eCtx, sessionId);
		}

		return false;
	}
	
	protected boolean sessionRemomve(ExecutionContext eCtx, String sessionId) {
		return queueRemove(eCtx, sessionId);
	}
	
	private void logSessionKeys() {
		logit.trace(()->"session size: "+sessionCache.size());
		
		sessionCache.keySet().stream()
			.forEach(key->logit.trace(()->"session key: "+key));
	}
	
	
	protected boolean sessionExists(ExecutionContext eCtx, String sessionId) {
		return queueExists(eCtx, sessionId);
	}
	
	protected QuadModel<?, ?> sessionGet(ExecutionContext eCtx, String sessionId) {
		return Optional.ofNullable(queueGet(eCtx, sessionId))
				.map(ExecutionContext::getQuadModel)
				.orElse(null);
	}
	
//	private static final InheritableThreadLocal<String> TH_SESSION = new InheritableThreadLocal<String>() {
//		@Override
//		protected String initialValue() {
//			return RequestContextHolder.getRequestAttributes().getSessionId();
//		}
//	};
//	
//	private String getSessionKey(ExecutionContext eCtx) {
//		logit.trace(()->"[getSessionKey] eCtx:"+eCtx+" for "+getSessionIdForLogging());
//		logSessionKeys();
//
//		String sessionId = TH_SESSION.get();
//		String ctxId = eCtx.getId();
//		
//		String key = ctxId +"_sessionId{"+sessionId+"}";
//		return key;
//	}
	
	private String getSessionKey(ExecutionContext eCtx, String sessionId) {
		logit.trace(()->"[getSessionKey] eCtx:"+eCtx+" for "+sessionId);
		logSessionKeys();
	
		String ctxId = eCtx.getId();
		
		String key = ctxId +"_sessionId{"+sessionId+"}";
		return key;
	}
	
	private boolean queueExists(ExecutionContext eCtx, String sessionId) {
		return sessionCache.containsKey(getSessionKey(eCtx, sessionId));
	}
	
	private ExecutionContext queueGet(ExecutionContext eCtx, String sessionId) {
		return sessionCache.get(getSessionKey(eCtx, sessionId));
	}
	
	private boolean queuePut(ExecutionContext eCtx, String sessionId) {
		synchronized (sessionCache) {
			sessionCache.put(getSessionKey(eCtx, sessionId), eCtx);
		}
		return true;
	}

	private boolean queueRemove(ExecutionContext eCtx, String sessionId) {
		// skip if doesn't exist
		if(!queueExists(eCtx, sessionId))
			return false;
		
		synchronized (sessionCache) {
			ExecutionContext removed = sessionCache.remove(getSessionKey(eCtx, sessionId));
			return removed!=null;
		}
	}
	
	@Override
	public void clear() {
		synchronized (sessionCache) {
			// shutdown
			sessionCache.values().stream()
				.forEach(e->{
					e.getQuadModel().getRoot().getExecutionRuntime().stop();
				});
			
			// clear cache
			sessionCache.clear();	
		}
	}
}
