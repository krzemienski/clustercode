package clustercode.api.scan;

import clustercode.api.domain.Media;
import clustercode.api.domain.Profile;
import clustercode.impl.util.Indexable;
import clustercode.impl.util.OptionalFunction;

/**
 * Note: This class has natural order that is inconsistent with equals.
 */
public interface ProfileMatcher extends OptionalFunction<Media, Profile>, Indexable<ProfileMatcher> {

}
