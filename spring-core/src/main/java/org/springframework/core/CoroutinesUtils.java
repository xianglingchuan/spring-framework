/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClassifier;
import kotlin.reflect.KFunction;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.reactor.MonoKt;
import kotlinx.coroutines.reactor.ReactorFlowKt;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Utilities for working with Kotlin Coroutines.
 *
 * @author Sebastien Deleuze
 * @author Phillip Webb
 * @since 5.2
 */
public abstract class CoroutinesUtils {

	/**
	 * Convert a {@link Deferred} instance to a {@link Mono}.
	 * 将{@link Deferred}实例转换为{@link Mono}。
	 */

	@SuppressWarnings("unchecked")
	public static <T> Mono<T> deferredToMono(Deferred<T> source) {
		return (Mono<T>) MonoKt.mono(Dispatchers.getUnconfined(),
				(scope, continuation) -> source.await((Continuation<? super T>) continuation));
	}

	/**
	 * Convert a {@link Mono} instance to a {@link Deferred}.
	 * 将{@link Mono}实例转换为{@link Deferred}实例。
	 */
	@SuppressWarnings("unchecked")
	public static <T> Deferred<T> monoToDeferred(Mono<T> source) {
		return (Deferred<T>) BuildersKt.async(GlobalScope.INSTANCE, Dispatchers.getUnconfined(),
				CoroutineStart.DEFAULT,
				(scope, continuation) -> MonoKt.awaitSingleOrNull(source, continuation));
	}

	/**
	 * Invoke a suspending function and converts it to {@link Mono} or
	 * {@link Flux}.
	 */
	@SuppressWarnings("deprecation") //添加去掉了"method.isAccessible()"获错警告信息
	//它可以用于抑制编译器产生的某些类型的警告，使得代码在编译时不会因为注解所指定的警告类型而显示警告信息。
	//抑制单类型的警告，如@SuppressWarnings("unchecked")用于抑制未检查的转换警告。
	//抑制多类型的警告，如@SuppressWarnings(value={"unchecked", "rawtypes"})用于同时抑制未检查的转换和未指定泛型的警告。
	//抑制所有类型的警告，如@SuppressWarnings("all")用于屏蔽所有类型的警告。
	public static Publisher<?> invokeSuspendingFunction(Method method, Object target, Object... args) {
		KFunction<?> function = Objects.requireNonNull(ReflectJvmMapping.getKotlinFunction(method));

		//报错 警告: [deprecation] AccessibleObject中的isAccessible()已过时 if (method.isAccessible() && !KCallablesJvm.isAccessible(function)) {
		if (method.isAccessible() && !KCallablesJvm.isAccessible(function)) {
			KCallablesJvm.setAccessible(function, true);
		}


		KClassifier classifier = function.getReturnType().getClassifier();
		Mono<Object> mono = MonoKt.mono(Dispatchers.getUnconfined(), (scope, continuation) ->
					KCallables.callSuspend(function, getSuspendedFunctionArgs(target, args), continuation))
				.filter(result -> !Objects.equals(result, Unit.INSTANCE))
				.onErrorMap(InvocationTargetException.class, InvocationTargetException::getTargetException);
		if (classifier != null && classifier.equals(JvmClassMappingKt.getKotlinClass(Flow.class))) {
			return mono.flatMapMany(CoroutinesUtils::asFlux);
		}
		return mono;
	}

	private static Object[] getSuspendedFunctionArgs(Object target, Object... args) {
		Object[] functionArgs = new Object[args.length];
		functionArgs[0] = target;
		System.arraycopy(args, 0, functionArgs, 1, args.length - 1);
		return functionArgs;
	}

	private static Flux<?> asFlux(Object flow) {
		return ReactorFlowKt.asFlux(((Flow<?>) flow));
	}

}
