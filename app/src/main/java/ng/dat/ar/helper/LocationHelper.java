//package ng.dat.ar.helper;
//
//import android.content.Context;
//import android.content.Intent;
//import android.location.Location;
//import android.net.Uri;
//import android.util.Log;
//import android.widget.Toast;
//
//import com.google.android.gms.maps.model.LatLng;
//
//import java.text.DecimalFormat;
//
//import ng.dat.ar.R;
//
///**
// * Created by ntdat on 1/13/17.
// */
//
//public class LocationHelper {
//    private final static double WGS84_A = 6378137.0;                  // WGS 84 semi-major axis constant in meters
//    private final static double WGS84_E2 = 0.00669437999014;          // square of WGS 84 eccentricity
//
//    public static float[] WSG84toECEF(Location location) {
//        double radLat = Math.toRadians(location.getLatitude());
//        double radLon = Math.toRadians(location.getLongitude());
//
//        float clat = (float) Math.cos(radLat);
//        float slat = (float) Math.sin(radLat);
//        float clon = (float) Math.cos(radLon);
//        float slon = (float) Math.sin(radLon);
//
//        float N = (float) (WGS84_A / Math.sqrt(1.0 - WGS84_E2 * slat * slat));
//
//        float x = (float) ((N + location.getAltitude()) * clat * clon);
//        float y = (float) ((N + location.getAltitude()) * clat * slon);
//        float z = (float) ((N * (1.0 - WGS84_E2) + location.getAltitude()) * slat);
//
//        return new float[]{x, y, z};
//    }
//
//    public static float[] ECEFtoENU(Location currentLocation, float[] ecefCurrentLocation, float[] ecefPOI) {
//        double radLat = Math.toRadians(currentLocation.getLatitude());
//        double radLon = Math.toRadians(currentLocation.getLongitude());
//
//        float clat = (float) Math.cos(radLat);
//        float slat = (float) Math.sin(radLat);
//        float clon = (float) Math.cos(radLon);
//        float slon = (float) Math.sin(radLon);
//
//        float dx = ecefCurrentLocation[0] - ecefPOI[0];
//        float dy = ecefCurrentLocation[1] - ecefPOI[1];
//        float dz = ecefCurrentLocation[2] - ecefPOI[2];
//
//        float east = -slon * dx + clon * dy;
//
//        float north = -slat * clon * dx - slat * slon * dy + clat * dz;
//
//        float up = clat * clon * dx + clat * slon * dy + slat * dz;
//
//        return new float[]{east, north, up, 1};
//    }
//
//    public static String calculationByDistance(LatLng StartP, LatLng EndP) {
//        int Radius = 6371;// radius of earth in Km
//        double lat1 = StartP.latitude;
//        double lat2 = EndP.latitude;
//        double lon1 = StartP.longitude;
//        double lon2 = EndP.longitude;
//        double dLat = Math.toRadians(lat2 - lat1);
//        double dLon = Math.toRadians(lon2 - lon1);
//        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
//                + Math.cos(Math.toRadians(lat1))
//                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2)
//                * Math.sin(dLon / 2);
//        double c = 2 * Math.asin(Math.sqrt(a));
//        double valueResult = Radius * c;
//        double km = valueResult / 1;
//        DecimalFormat newFormat = new DecimalFormat("####");
//        int kmInDec = Integer.valueOf(newFormat.format(km));
//        double meter = valueResult % 1000;
//        int meterInDec = Integer.valueOf(newFormat.format(meter));
//        Log.i("Radius Value", "" + valueResult + "   KM  " + kmInDec
//                + " Meter   " + meterInDec);
//
//        return String.format("Distance : %.2f M", valueResult*1000);
//    }
//
//    /**
//     * calculates the distance between two locations in MILES
//     */
//    public static double distanceDiff(LatLng StartP, LatLng EndP) {
//
//        int Radius = 6371;// radius of earth in Km
//        double lat1 = StartP.latitude;
//        double lat2 = EndP.latitude;
//        double lon1 = StartP.longitude;
//        double lon2 = EndP.longitude;
//        double dLat = Math.toRadians(lat2 - lat1);
//        double dLon = Math.toRadians(lon2 - lon1);
//        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
//                + Math.cos(Math.toRadians(lat1))
//                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2)
//                * Math.sin(dLon / 2);
//        double c = 2 * Math.asin(Math.sqrt(a));
//        double valueResult = Radius * c;
//        double km = valueResult / 1;
//        DecimalFormat newFormat = new DecimalFormat("####");
//        int kmInDec = Integer.valueOf(newFormat.format(km));
//        double meter = valueResult % 1000;
//        int meterInDec = Integer.valueOf(newFormat.format(meter));
//        Log.i("Radius Value", "" + valueResult + "   KM  " + kmInDec
//                + " Meter   " + meterInDec);
//
//        return  valueResult*1000;
//    }
//
//    public static void openGoogleMaps(Context context, LatLng srcLatLong, LatLng destLatLong) {
//        Intent intent;
//        try {
//            Uri.Builder builder = new Uri.Builder();
//            builder.scheme("http")
//                    .authority("maps.google.com")
//                    .appendPath("maps")
//                    .appendQueryParameter("saddr", srcLatLong.latitude + "," + srcLatLong.longitude)
//                    .appendQueryParameter("daddr", destLatLong.latitude + "," + destLatLong.longitude);
//
//            intent = new Intent(android.content.Intent.ACTION_VIEW,
//                    Uri.parse(builder.build().toString()));
//            context.startActivity(intent);
//        } catch (Exception e) {
//            Toast.makeText(context, "Something went wrong! ", Toast.LENGTH_SHORT).show();
//        }
//    }
//}
