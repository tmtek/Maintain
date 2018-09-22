package com.tmtek.rx;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Single;
import io.reactivex.SingleTransformer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.BiFunction;
import io.reactivex.subjects.BehaviorSubject;

public class Maintain {

	/**
	 * Toggle instances allow a simple on/off state to be maintained to be used in conjunction
	 * with updateIf functions and Maintain.latest(). It allows external entities to specify that they
	 * desire an update when the next subscription to a maintained value is made.
	 */
	public static class Toggle {

		private final BehaviorSubject<Boolean> mStateStream;

		public Toggle(final boolean initialState) {
			mStateStream = BehaviorSubject.createDefault(initialState);
		}

		public Toggle() {
			this(false);
		}

		/**
		 * Sets the Toggle's current state to on.
		 */
		public Single<Boolean> on() {
			mStateStream.onNext(true);
			return Single.just(true);
		}

		/**
		 * @return true if the Toggle's current state is on.
		 */
		public boolean isOn() {
			return mStateStream.getValue();
		}

		/**
		 * Sets the Toggle's current state to off.
		 */
		public Single<Boolean> off() {
			mStateStream.onNext(false);
			return Single.just(true);
		}
	}

	/**
	 * A collection of commonly used update functions.
	 */
	public static class Update {

		/**
		 * Use this update function with latest() to trigger an update only when the supplied Single<Boolean>
		 *     emits  value of true.
		 * @param stream The Single<Boolean> that will supply the boolean value.
		 * @param <T> The emission type.
		 * @return A BiFunction<T, Long, Single<Boolean>>.
		 */
		public static <T> BiFunction<T, Long, Single<Boolean>> when(final Single<Boolean> stream) {
			return (t, lastUpdateInMillis) -> stream;
		}

		/**
		 * Use this update function with latest() to store the first result and perform no further updates.
		 * @param <T> The emission type.
		 * @return BiFunction<T, Long, Single<Boolean>> that always returns false.
		 */
		public static <T> BiFunction<T, Long, Single<Boolean>> once() {
			return (t, lastUpdateInMillis) -> Single.just(false);
		}

		/**
		 * Use this update function with latest() to force the subject to be updated whenever possible.
		 * @param <T> The emission type.
		 * @return A BiFunction<T, Long, Single<Boolean>> that always returns true.
		 */
		public static <T> BiFunction<T, Long, Single<Boolean>> always() {
			return (t, lastUpdateInMillis) -> Single.just(true);
		}

		/**
		 * Use this function with latest() to expire any existing maintained result once the specified period
		 * of time elapses. When the time period passes, the next subscription will trigger an update. Upon completion,
		 * the clock is reset.
		 * @param period The duration of time in units that must elapse before the maintained value is considered expired.
		 * @param periodUnit The TimeUnit that period is measured in.
		 * @param <T> The emission type.
		 * @return A BiFunction<T, Long, Single<Boolean>> that returns true once the specified period of time has elapsed since
		 * the last emission.
		 */
		public static <T> BiFunction<T, Long, Single<Boolean>> expired(final long period, final TimeUnit periodUnit) {
			return (t, lastUpdateInMillis) -> {
				final long now = System.currentTimeMillis();
				final long elapsedSinceLast = now - lastUpdateInMillis;
				final long periodMillis = periodUnit.toMillis(period);
				return Single.just(elapsedSinceLast > periodMillis);
			};
		}

		/**
		 * Use this function with latest() to allow an update only when the isOn() value of the Toggle returns true.
		 * This allows the maintained value to be updated on demand by external conditions while still maintaining
		 * centralized control. When this function executes, it will always call the Toggle.off() method.
		 * @param updateToggle An instance of Switch.
		 * @param <T> The emission type.
		 * @return A BiFunction<T, Long, Boolean> that returns true when the isOn() value of the supplied Toggle is true.
		 */
		public static <T> BiFunction<T, Long, Single<Boolean>> toggledOn(final Toggle updateToggle) {
			return (t, lastUpdateInMillis) -> {
				final boolean currentState = updateToggle.isOn();
				updateToggle.off();
				return Single.just(currentState);
			};
		}

		/**
		 * Use this function with latest() to list a series of update functions that are checked to see if
		 * they require the maintained value to be updated.
		 *
		 * It must be known that all supplied functions are tested every time this function executes.
		 * This ensures that any state that might be managed in each of the functions has a chance to be updated.
		 * The loop does not break if any of the functions return true.
		 * @param matchAll If true, all conditions must return true. If false, only one needs to return true.
		 * @param conditions An array of BiFunction<T, Long, Single<Boolean>> update functions.
		 * @param <T> The emission type.
		 * @return A BiFunction<T, Long, Boolean> that returns true if any of the supplied functions return true.
		 */
		public static <T> BiFunction<T, Long, Single<Boolean>> conditions(final boolean matchAll, final BiFunction<T, Long, Single<Boolean>>... conditions) {
			return (t, lastUpdateInMillis) -> {
				boolean requiresUpdate = false;

				final List<Observable<Boolean>> conditionChecks = new ArrayList<>();
				for (BiFunction<T, Long, Single<Boolean>> condition : conditions) {
					conditionChecks.add(condition.apply(t, lastUpdateInMillis).toObservable());
				}


				return Observable.merge(conditionChecks)
					.filter(v -> matchAll ? !v : v)
					.firstOrError()
					.onErrorResumeNext(Single.just(matchAll));
			};
		}
	}

	/**
	 * Use this Transformer to transform your upstream single into an update stream for a maintained value.
	 * @param <T> The upstream emission type.
	 * @return A SingleTransformer<T, T>.
	 */
	public static <T> SingleTransformer<T, T> latest() {
		return upstream -> latest(upstream, Update.once());
	}

	/**
	 * Use this Transformer to transform your upstream single into an update stream for a maintained value.
	 * @param updateIf An update function to use to determine if the update stream is triggered upon the next subscription.
	 * @param <T>The upstream emission type.
	 * @return A SingleTransformer<T, T>.
	 */
	public static <T> SingleTransformer<T, T> latest(final BiFunction<T, Long, Single<Boolean>> updateIf) {
		return upstream -> latest(upstream, updateIf);
	}

	/**
	 * Use this Transformer to transform your upstream single into an update stream for a maintained value.
	 * @param updateIf An update function to use to determine if the update stream is triggered upon the next subscription.
	 * @param subject A BehaviourSubject that is updated with the resulting updates.
	 * @param <T> The upstream emission type.
	 * @return A SingleTransformer<T, T>.
	 */
	public static <T> SingleTransformer<T, T> latest(final BiFunction<T, Long, Single<Boolean>> updateIf, final BehaviorSubject<T> subject) {
		return upstream -> latest(upstream, updateIf, subject);
	}

	/**
	 * This method allows you to create a stream that updates an internal BehaviourSubject with a shared stream and control
	 * the conditions of updating.
	 * @param updateStream The stream to use to update the BehaviourSubject.
	 * @param updateIf  A BiFunction that returns true if we want the maintained value to be updated, false if we want to use
	 *                    an existing value. If there is no existing value, an update will occur.
	 *                    The first argument to the function is the current value being maintained.
	 *                    The second argument is the time in milliseconds when the maintained value was last updated.
	 * @param <T> The Emission type.
	 * @return A Single<T>, but note that this stream is backed back a connectable stream, so all subscriptions made
	 * while an update is running will be shared.
	 */
	private static <T> Single<T> latest(final Single<T> updateStream, BiFunction<T, Long, Single<Boolean>> updateIf) {
		return latest(updateStream, updateIf, BehaviorSubject.create());
	}

	/**
	 * This method allows you to create a stream that updates a BehaviourSubject with a shared stream and control
	 * the conditions of updating.
	 * @param updateStream The stream to use to update the BehaviourSubject.
	 * @param updateIf A BiFunction that returns true if we want the subject to be updated, false if we want to use
	 *                    an existing value. If there is no existing value, an update will occur.
	 *                    The first argument to the function is the current value being maintained.
	 *                    The second argument is the time in milliseconds when the maintained value was last updated.
	 * @param subject The BehaviorSubject to update.
	 * @param <T> The Emission type.
	 * @return A Single<T>, but note that this stream is backed back a connectable stream, so all subscriptions made
	 * while an update is running will be shared.
	 */
	private static <T> Single<T> latest(final Single<T> updateStream, BiFunction<T, Long, Single<Boolean>> updateIf, final BehaviorSubject<T> subject) {
		return Observable.create(new ObservableOnSubscribe<T>() {

			private BehaviorSubject<T> mSubject = subject;

			private Disposable mSubjectStreamDisp = subject.doOnNext(v -> {
				lastUpdateMillis = System.currentTimeMillis();
			}).subscribe();

			private long lastUpdateMillis = 0;

			@Override
			public void subscribe(final ObservableEmitter<T> e) throws Exception {
				final T currentValue = mSubject.getValue();

				final Single<Boolean> checkForUpdate = currentValue == null ?
						Single.just(true) :
						updateIf.apply(currentValue, lastUpdateMillis);

				final Disposable disposable = checkForUpdate
					.flatMap(update -> update ?
						withUpdateFor(mSubject, updateStream)
						/*
						TODO: subscribe to the subject to update this.
						That will make it ok to update an external BehaviorSubject manually.
						 */
						.doOnSuccess(value -> lastUpdateMillis = System.currentTimeMillis())
							:
						withLatestFrom(mSubject, updateStream)
					)
					.subscribe(
						val -> {
							e.onNext(val);
							e.onComplete();
						},
						throwable -> e.onError(throwable)
					);
				e.setCancellable(() -> {
					Log.d("test", "disposing");
					disposable.dispose();
				});
			}
		}).publish().refCount().singleOrError();
	}

	/**
	 * This method retrieves the latest value from a BehaviourSubject if it exists and returns it wrapped in a Single.
	 * If a value does not yet exist, the updateStream is returned, but upon the successful completion of the stream,
	 * the value will be added to the BehaviourSubject.
	 * @param subject A BehaviourSubject.
	 * @param updateStream A Single that updates the BehaviourSubject if no value exists.
	 * @param <T> The emission type for the subject and update stream.
	 * @return A Single<T>.
	 */
	private static <T> Single<T> withLatestFrom(final BehaviorSubject<T> subject, final Single<T> updateStream) {
		final T currentValue = subject.getValue();
		return currentValue!= null ? Single.just(currentValue) : withUpdateFor(subject, updateStream);
	}

	/**
	 * This method updates the supplied BehaviorSubject with the successful result of the update stream.
	 * @param subject A BehaviourSubject.
	 * @param updateStream A Single that updates the BehaviourSubject.
	 * @param <T> The emission type for the subject and update stream.
	 * @return A Single<T>.
	 */
	private static <T> Single<T> withUpdateFor(final BehaviorSubject<T> subject, final Single<T> updateStream) {
		return updateStream.doOnSuccess(value -> subject.onNext(value));
	}
}
