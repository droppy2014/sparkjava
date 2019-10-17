import com.google.gson.*;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import org.json.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;

import static spark.Spark.*;

public class HelloWorld {

    static JSONArray jobs;
    static JSONArray vehicles;
    static JSONArray locations;
    static VehicleRoutingProblem.Builder vrpBuilder;

    public static void main(String[] args) {

        port(1234);
        //stop();

        //get("/hello", (req, res) -> "Hello World!!xx!");

        post("/hello", (request, response) -> {
            response.type("application/json");
            jobs = null;
            vehicles = null;
            locations = null;
            vrpBuilder =  VehicleRoutingProblem.Builder.newInstance();

            //getMatrix();

            //VehicleRoutingProblemSolution bestSolution = (VehicleRoutingProblemSolution) findBestSolution();
            //String res = request.body();

            try {
                JSONObject responceObj = new JSONObject(request.body());
                jobs = responceObj.getJSONArray("jobs");
                vehicles = responceObj.getJSONArray("vehicles");
                locations = responceObj.getJSONArray("locations");
                createMatrix();
                createJobs();
                //createSolution();

            } catch (JSONException e) {
                // log or consume it some other way
                System.out.println(e);
            }
            return new Gson().toJson("ok");
        });

    }

    private static void createJobs() {
        //////CONTINUE THIS
        for (int j = 0 ; j < jobs.length(); j++) {
            JSONObject job = jobs.getJSONObject(j);
            String pickup_from = job.getString("pickup_from");
            String delivery_to = job.getString("delivery_to");
            String job_id = job.getString("job_id");
            Shipment shipment = Shipment.Builder.newInstance(job_id)
                    .setName("myShipment")
                    .setPickupLocation(Location.newInstance(pickup_from))
                    .setDeliveryLocation(Location.newInstance(delivery_to))
                    .addSizeDimension(0,9)
                    .addSizeDimension(1,50)
                    .addRequiredSkill("loading bridge").addRequiredSkill("electric drill")
                    .build();


        }
    }

    private static void createMatrix() {
        String coords_line = "";
        for (int j = 0 ; j < locations.length(); j++) {
            JSONObject location = locations.getJSONObject(j);
            JSONArray location_coord = location.getJSONArray("location");
            float location_coord_lat = location_coord.getFloat(0);
            float location_coord_lng = location_coord.getFloat(1);
            coords_line = coords_line + ";" + location_coord_lat + "," + location_coord_lng;
            //System.out.println("matrix_cell_value " + matrix_cell_value);
        }
        coords_line = coords_line.substring(1);
        System.out.println(coords_line);
        requestMatrix(coords_line);
    }

    private static void requestMatrix(String coords_line) {
        String sURL = "http://95.217.33.235:5000/table/v1/driving/"+coords_line+"?annotations=duration,distance";
        try {
            URL url = new URL(sURL);
            URLConnection request = url.openConnection();
            request.connect();
            JsonParser jp = new JsonParser(); //from gson
            JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent())); //Convert the input stream to a json element
            JsonObject gson_matrix_obj = root.getAsJsonObject();
            JSONObject matrix_obj = new JSONObject(gson_matrix_obj.toString());
            createTimeMatrix(matrix_obj);
            createDistanceMatrix(matrix_obj);
            System.out.println("responce " + matrix_obj);

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createTimeMatrix(JSONObject matrix_obj) {

        VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);

        JSONArray durations = matrix_obj.getJSONArray("durations");
        for (int r = 0 ; r < durations.length(); r++) {
            JSONArray matrix_row = durations.getJSONArray(r);
            for (int c = 0 ; c < matrix_row.length(); c++) {
                float matrix_cell_value = matrix_row.getFloat(c);
                String row_num = String.valueOf(r);
                String cell_num = String.valueOf(c);
                costMatrixBuilder.addTransportTime(row_num, cell_num, matrix_cell_value);
                //System.out.println("matrix_cell_value " + matrix_cell_value);
            }
        }
    }

    private static void createDistanceMatrix(JSONObject matrix_obj) {

        VehicleRoutingTransportCostsMatrix.Builder costMatrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);

        JSONArray distances = matrix_obj.getJSONArray("distances");
        for (int r = 0 ; r < distances.length(); r++) {
            JSONArray matrix_row = distances.getJSONArray(r);
            for (int c = 0 ; c < matrix_row.length(); c++) {
                float matrix_cell_value = matrix_row.getFloat(c);
                String row_num = String.valueOf(r);
                String cell_num = String.valueOf(c);
                costMatrixBuilder.addTransportDistance(row_num, cell_num, matrix_cell_value);
                //System.out.println("matrix_cell_value " + matrix_cell_value);
            }
        }
    }

    private static void createSolution() {

        for (int i = 0 ; i < vehicles.length(); i++) {
            JSONObject vehicle = vehicles.getJSONObject(i);
            addVehicle(vehicle);
        }
    }

    private static void addVehicle(JSONObject vehicle) {

        System.out.println("extract vehicle");
        System.out.println(vehicle);

        VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder.newInstance("vehicleType")
                .addCapacityDimension(0,30).addCapacityDimension(1,100)
                .build();
        VehicleImpl new_vehicle = VehicleImpl.Builder.newInstance(String.valueOf(vehicle.getInt("id")))
                .setType(vehicleType)
                .setStartLocation(Location.newInstance(0,0)).setEndLocation(Location.newInstance(20,20))
                .addSkill("loading bridge").addSkill("electric drill")
                .build();
        vrpBuilder.addVehicle(new_vehicle);
    }


    private static Object findBestSolution() {
        final int WEIGHT_INDEX = 0;
        VehicleTypeImpl.Builder vehicleTypeBuilder = VehicleTypeImpl.Builder.newInstance("vehicleType").addCapacityDimension(WEIGHT_INDEX,2);
        VehicleType vehicleType = vehicleTypeBuilder.build();
        /*
         * get a vehicle-builder and build a vehicle located at (10,10) with type "vehicleType"
         */
        VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder.newInstance("vehicle");
        vehicleBuilder.setStartLocation(Location.newInstance(10, 10));
        vehicleBuilder.setType(vehicleType);
        VehicleImpl vehicle = vehicleBuilder.build();

        /*
         * build services with id 1...4 at the required locations, each with a capacity-demand of 1.
         * Note, that the builder allows chaining which makes building quite handy
         */

        Service service1 = Service.Builder.newInstance("1").addSizeDimension(WEIGHT_INDEX,1).setLocation(Location.newInstance(5, 7)).build();
        Service service2 = Service.Builder.newInstance("2").addSizeDimension(WEIGHT_INDEX,1).setLocation(Location.newInstance(5, 13)).build();
        Service service3 = Service.Builder.newInstance("3").addSizeDimension(WEIGHT_INDEX,1).setLocation(Location.newInstance(15, 7)).build();
        Service service4 = Service.Builder.newInstance("4").addSizeDimension(WEIGHT_INDEX,1).setLocation(Location.newInstance(15, 13)).build();

        /*
         * again define a builder to build the VehicleRoutingProblem
         */
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();
        vrpBuilder.addVehicle(vehicle);
        vrpBuilder.addJob(service1).addJob(service2).addJob(service3).addJob(service4);

        /*
         * build the problem
         * by default, the problem is specified such that FleetSize is INFINITE, i.e. an infinite number of
         * the defined vehicles can be used to solve the problem
         * by default, transport costs are computed as Euclidean distances
         */

        VehicleRoutingProblem problem = vrpBuilder.build();

        /*
         * get the algorithm out-of-the-box.
         */
        VehicleRoutingAlgorithm algorithm = Jsprit.createAlgorithm(problem);

        /*
         * and search a solution which returns a collection of solutions (here only one solution is constructed)
         */

        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();

        /*
         * use the static helper-method in the utility class Solutions to get the best solution (in terms of least costs)
         */

        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        return bestSolution;

        //SolutionPrinter.print(problem, bestSolution, SolutionPrinter.Print.CONCISE);
        //SolutionPrinter.print(problem, bestSolution, SolutionPrinter.Print.VERBOSE);

        //System.out.println(problem);
        //System.out.println(bestSolution);

        //Gson gson = new Gson();
        //gson.toJson(1);



        //User user = new Gson().fromJson(request.body(), User.class);
        // userService.addUser(user);
    }


}
