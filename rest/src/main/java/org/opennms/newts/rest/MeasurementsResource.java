package org.opennms.newts.rest;


import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.opennms.newts.api.Measurement;
import org.opennms.newts.api.Results;
import org.opennms.newts.api.SampleRepository;
import org.opennms.newts.api.Timestamp;
import org.opennms.newts.api.query.ResultDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.yammer.metrics.annotation.Timed;


@Path("/measurements")
@Produces(MediaType.APPLICATION_JSON)
public class MeasurementsResource {

    private static final Logger LOG = LoggerFactory.getLogger(MeasurementsResource.class);

    private final SampleRepository m_repository;
    private final Map<String, ResultDescriptorDTO> m_reports;

    public MeasurementsResource(SampleRepository repository, Map<String, ResultDescriptorDTO> reports) {
        m_repository = checkNotNull(repository, "repository argument");
        m_reports = checkNotNull(reports, "reports argument");
    }

    @GET
    @Path("/{report}/{resource}")
    @Timed
    public Results<Measurement> getMeasurements(
            @PathParam("report") String report,
            @PathParam("resource") String resource,
            @QueryParam("start") Optional<String> start,
            @QueryParam("end") Optional<String> end,
            @QueryParam("resolution") Optional<String> resolutionParam) {

        /*
         * XXX: This resource method should accept a DurationParam instance for the resolution query
         * parameter, and TimestampParam for start/end. However, for reasons I cannot not (yet) fathom,
         * Jersey bitches about a missing dependency at startup, and the resource is not loaded. 
         *
         * ERROR [2014-03-19 20:20:31,705] com.sun.jersey.spi.inject.Errors: The following errors and
         * warnings have been detected with resource and/or provider classes:
         *    SEVERE: Missing dependency for method public java.util.Collection org.opennms.newts.rest.MeasurementsResource.getMeasurements(java.lang.String,java.lang.String,com.google.common.base.Optional,com.google.common.base.Optional,org.opennms.newts.rest.DurationParam)
         * at parameter at index 4
         *
         * ETOOMUCHMAGIC
         *
         */
        Optional<Timestamp> lower = Transform.timestampFromString(start);
        Optional<Timestamp> upper = Transform.timestampFromString(end);

        if (!resolutionParam.isPresent()) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("the 'resolution' query argument is mandatory (for the time being)")
                            .build());
        }

        DurationParam resolution = new DurationParam(resolutionParam.get());

        LOG.debug(
                "Retrieving measurements for report {}, resource {}, from {} to {} w/ resolution {}",
                report,
                resource,
                lower,
                upper,
                resolution.get());

        ResultDescriptorDTO descriptorDTO = m_reports.get(report);

        // Report not found; 404
        if (descriptorDTO == null) {
            return null;
        }

        ResultDescriptor rDescriptor = Transform.resultDescriptor(descriptorDTO);

        return m_repository.select(resource, lower, upper, rDescriptor, resolution.get());
    }

}
