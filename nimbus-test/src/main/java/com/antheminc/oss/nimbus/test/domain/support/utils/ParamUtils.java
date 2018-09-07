/**
 *  Copyright 2016-2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.antheminc.oss.nimbus.test.domain.support.utils;

import java.util.Locale;

import com.antheminc.oss.nimbus.domain.cmd.exec.CommandExecution.MultiOutput;
import com.antheminc.oss.nimbus.domain.cmd.exec.CommandExecution.Output;
import com.antheminc.oss.nimbus.domain.model.state.EntityState.Param;
import com.antheminc.oss.nimbus.domain.model.state.EntityState.Param.LabelState;
import com.antheminc.oss.nimbus.support.Holder;

/**
 * @author Tony Lopez
 *
 */
public class ParamUtils {

	public static <T> String getLabelText(Param<T> param) {
		return getLabelText(param, Locale.getDefault().toLanguageTag());
	}

	public static <T> String getLabelText(Param<T> param, String locale) {
		if (null == param.getLabels()) {
			throw new RuntimeException("Unable to locate label config for " + param);
		}

		LabelState labelConfig = param.getLabels().stream().filter(lc -> locale.equals(lc.getLocale()))
				.reduce((a, b) -> {
					throw new IllegalStateException("Found more than one element");
				}).orElse(null);

		if (null == labelConfig) {
			throw new RuntimeException("Unable to locate label config using locale: " + locale + " for " + param);
		}

		return labelConfig.getText();
	}
	
	/**
	 * <p>
	 * Given a framework response object, {@code response}, this method
	 * deciphers and attempts to locate a param from each of the
	 * {@link MultiOutput}'s {@link Output#getValue()} values that has a URI
	 * path ending with {@code paramPathEndsWith}. If multiple params are found,
	 * only the first will be returned.
	 * <p>
	 * If {@code paramPathEndsWith} is {@code null} this method will return the
	 * result of {@link MultiOutput#getSingleResult()}.
	 * 
	 * @throws RuntimeException
	 *             if {@code paramPathEndsWith} is provided and not contained by
	 *             any of the outputs deciphered in {@code response}
	 * @param response
	 *            the response received as a result of the framework request
	 * @param paramPathEndsWith
	 *            the ending path of the param to identify, from the set of
	 *            params received in the {@code response}
	 * @return the param identified within the response ending with
	 *         {@code paramPathEndsWith}
	 */
	@SuppressWarnings("unchecked")
	public static <T> Param<T> extractResponseByParamPath(Object response, String paramPathEndsWith) {
		Holder<MultiOutput> resp = (Holder<MultiOutput>) response;
		MultiOutput multiOutput = resp.getState();

		if (null == paramPathEndsWith) {
			return (Param<T>) multiOutput.getSingleResult();
		}

		for (Output<?> output : multiOutput.getOutputs()) {
			if (output.getValue() instanceof Param) {
				Param<?> param = (Param<?>) output.getValue();
				if (param.getPath().endsWith(paramPathEndsWith)) {
					return (Param<T>) param;
				}
			}
		}

		throw new RuntimeException("Unable to locate param in response ending with '" + paramPathEndsWith
				+ "'. See debug logs for more information.");
	}
}
