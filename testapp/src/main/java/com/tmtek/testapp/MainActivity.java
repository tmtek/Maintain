package com.tmtek.testapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.tmtek.rx.Maintain;

import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import io.reactivex.subjects.BehaviorSubject;

public class MainActivity extends AppCompatActivity {


	private Integer mCounter = 0;

	@SuppressLint("CheckResult")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Maintain.Toggle toggle = new Maintain.Toggle();

		final BehaviorSubject<Integer> updatingStream = BehaviorSubject.create();

		final Single<Boolean> checkUpdateStream = Single.just(false);
		final Single<Boolean> checkUpdateStream2 = Single.just(true);

		final Single<Integer> timerStream = Single.defer(() ->
				Single.just(mCounter++)
			)
			.delay(3, TimeUnit.SECONDS)
			.compose(Maintain.latest(
				Maintain.Update.conditions(
					false,
					Maintain.Update.when(checkUpdateStream),
					Maintain.Update.when(checkUpdateStream2)
				),
				updatingStream
			 ));


		Log.d("test", "begin");
		updatingStream.subscribe(v -> {
			Log.d("test", "updating stream emission:" + v);
		});

		timerStream.subscribe(v -> {
			Log.d("test", "stream 1 emission:" + v);
		});

		timerStream.subscribe(v -> {
			Log.d("test", "stream 2 emission:" + v);
		});

		Single.timer(2, TimeUnit.SECONDS)
			.flatMap(v -> timerStream)
			.subscribe(v -> {
				Log.d("test", "stream 3 emission:" + v);
			});

		Single.timer(4, TimeUnit.SECONDS)
			.flatMap(v -> timerStream)
			.subscribe(v -> {
				Log.d("test", "stream 4 emission:" + v);
			});

	}

}
