package ng.dat.ar.network;

import ng.dat.ar.model.DirectionsResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Created by Amal Krishnan on 06-03-2017.
 */

public interface RetrofitInterface {

    @GET("maps/api/directions/json?")
    Call<DirectionsResponse> getDirections(
            @Query("origin") String origin,
            @Query("destination") String destination,
            @Query("key") String key
    );



}

