package clustercode.api.rest.v1.rest;

import clustercode.api.cluster.ClusterTask;
import clustercode.api.rest.v1.RestServicesActivator;
import clustercode.api.rest.v1.dto.ApiError;
import clustercode.api.rest.v1.dto.Task;
import clustercode.api.rest.v1.hook.TaskHook;
import io.swagger.annotations.*;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Date;
import java.text.DecimalFormat;
import java.util.stream.Collectors;

@Path(RestServicesActivator.REST_API_CONTEXT_PATH + "/tasks")
@Api(description = "the tasks API")
public class TasksApi extends AbstractRestApi {

    private static final DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private final TaskHook taskHook;

    @Inject
    TasksApi(TaskHook taskHook) {
        this.taskHook = taskHook;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(
        value = "Tasks information",
        notes = "Provides operations to get task information. Completed tasks do not appear in the list.",
        response = Task.class, responseContainer = "List", tags = {"Tasks"})
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "An Array of Task object.", response = Task.class, responseContainer =
            "List"),
        @ApiResponse(code = 500, message = "Unexpected error", response = ApiError.class)})
    public Response getTasks() {
        return createResponse(() -> taskHook
            .getClusterTasks()
            .stream()
            .map(this::convertToDto)
            .collect(Collectors.toList()));
    }

    @DELETE
    @Path("/stop")
    @Produces({MediaType.APPLICATION_JSON})
    @ApiOperation(
        value = "",
        notes = "Stops the task that is currently being processed by the given hostname. If successful, the affected " +
            "node will transition to state WAIT.",
        response = Boolean.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Stopped the task successfully."),
        @ApiResponse(code = 409, message = "The task has not been found."),
        @ApiResponse(code = 412, message = "The parameters were not fully or correctly specified"),
        @ApiResponse(code = 500, message = "Unexpected error", response = ApiError.class)
    })
    public Response stopTask(
        @QueryParam("hostname")
        @ApiParam(
            value = "host name of the node, as returned by /tasks. If this parameter is omitted, then the node " +
                "of the current API endpoint will cancel its task.",
            required = true)
            String hostname
    ) {
        log.debug("Hostname: {}", hostname);
        if (hostname == null) return Response.status(Response.Status.PRECONDITION_FAILED).build();
        try {
            boolean cancelled = taskHook.cancelTask(hostname);
            if (cancelled) return Response.ok().build();
            return Response.status(Response.Status.CONFLICT).build();
        } catch (Exception ex) {
            log.catching(ex);
            return serverError(ex);
        }
    }

    private Task convertToDto(ClusterTask clusterTask) {
        return Task.builder()
                   .priority(clusterTask.getPriority())
                   .source(clusterTask.getSourceName())
                   .added(Date.from(clusterTask.getDateAdded().toInstant()))
                   .updated(Date.from(clusterTask.getLastUpdated().toInstant()))
                   .nodename(clusterTask.getMemberName())
                   .progress(Double.parseDouble(decimalFormat.format(clusterTask.getPercentage())))
                   .build();
    }
}

