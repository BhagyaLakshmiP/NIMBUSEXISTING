/**
 * 
 */
package com.anthem.oss.nimbus.core.domain.command.execution.nav;

import java.util.Optional;

import org.apache.commons.lang.StringUtils;

import com.anthem.oss.nimbus.core.BeanResolverStrategy;
import com.anthem.oss.nimbus.core.domain.command.Command;
import com.anthem.oss.nimbus.core.domain.command.execution.CommandMessageConverter;
import com.anthem.oss.nimbus.core.domain.command.execution.ExecutionContext;
import com.anthem.oss.nimbus.core.domain.command.execution.FunctionHandler;
import com.anthem.oss.nimbus.core.domain.definition.Constants;
import com.anthem.oss.nimbus.core.domain.definition.InvalidConfigException;
import com.anthem.oss.nimbus.core.domain.model.state.EntityState.Param;

/**
 * @author Soham Chakravarti
 *
 */
public class DefaultActionNewInitEntityFunctionHandler<T> implements FunctionHandler<T, Param<T>> {

	private final CommandMessageConverter converter;
	
	public DefaultActionNewInitEntityFunctionHandler(BeanResolverStrategy beanResolver) {
		this.converter = beanResolver.get(CommandMessageConverter.class);
	}
	
	@Override
	public Param<T> execute(ExecutionContext eCtx, Param<T> actionParameter) {
		Command cmd = eCtx.getCommandMessage().getCommand();
		
		String targetParamPath = Optional.ofNullable(cmd.getFirstParameterValue(Constants.KEY_FN_INITSTATE_ARG_TARGET_PATH.code))
									.orElseThrow(()->new InvalidConfigException("'"+Constants.KEY_FN_INITSTATE_ARG_TARGET_PATH.code+"' must be configured but found null for cmd:"+cmd));
		
		Param<Object> targetParam = Optional.ofNullable(actionParameter.findParamByPath(targetParamPath))
									.orElseThrow(()->new InvalidConfigException("No param for configured target path: "+targetParamPath+" for cmd: "+cmd));
		
		Class<?> convertToClass = targetParam.getConfig().getReferredClass();

		Object converted = resolveTargetState(cmd, targetParam, convertToClass);
		targetParam.setState(converted);
		
		return actionParameter;
	}
	
	protected Object resolveTargetState(Command cmd, Param<Object> targetParam, Class<?> convertToClass) {

		String json = Optional.ofNullable(cmd.getFirstParameterValue(Constants.KEY_FN_INITSTATE_ARG_JSON.code)).map(StringUtils::trimToNull)
						.orElseThrow(()->new InvalidConfigException("'json' OR must be configured but found null for cmd:"+cmd));

		Object converted = converter.convert(convertToClass, json);
		return converted;
	}
}
