package de.wr.rxextensions.xpoint;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.inject.Inject;

import de.wr.rxextensions.DaggerSampleComponent;
import de.wr.rxextensions.HasName;
import de.wr.rxextensions.R;
import de.wr.rxextensions.SampleComponent;

aspect AppStartNotifier {

    pointcut postInit(): execution(* android.app.Activity.onCreate(*));

    after() returning: postInit() {
        Activity app = (Activity) thisJoinPoint.getTarget();
        NotificationManager nmng = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        nmng.notify(9999, new NotificationCompat.Builder(app)
                .setTicker("Hello AspectJ")
                .setContentTitle("Notification from aspectJ")
                .setContentText("privileged aspect AppAdvice")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build());
    }

    public String AppCompatActivity.test() {
        return "test";
    }

    declare parents: AppCompatActivity implements HasName;

    private String HasName.name = "Hallo Welt";
    public  String HasName.getName()  { return name; }

    private static SampleComponent component = DaggerSampleComponent.builder().build();


    Object around() : get(!private * *) && @annotation(Inject) {
        Object value = proceed();
        if (value == null) {
            String fieldName = thisJoinPoint.getSignature().getName();
            Object obj = thisJoinPoint.getThis();

            try{
                Field field = obj.getClass().getDeclaredField(fieldName);
                Method[] methods = component.getClass().getMethods();
                for (Method method : methods) {
                    if(method.getReturnType().equals(field.getType())) {
                        field.set(obj, value = method.invoke(component));
                        break;
                    }
                }
            }
            catch( IllegalAccessException | NoSuchFieldException | InvocationTargetException e){e.printStackTrace();}
        }
        return value;
    }
}