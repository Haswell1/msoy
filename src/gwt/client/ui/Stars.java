//
// $Id$

package client.ui;

import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;

/**
 * A widget for displaying and optionally registering input on a row of 5 stars, i.e. for ratings.
 */
public class Stars extends FlowPanel
{
    public interface StarMouseListener
    {
        void starClicked (byte newRating);
        void starMouseOn (byte rating);
        void starMouseOff ();
    }

    public Stars (float rating, boolean isAverage, boolean halfSize, StarMouseListener handler)
    {
        _starType = (isAverage ? "average" : "user");
        _halfSize = halfSize;
        _handler = handler;

        setStyleName("stars");
        if (halfSize) {
            addStyleName("starsSmall");
        }

        // add the 10 images whose src url's we mercilessly mutate throughout this widget
        for (int ii = 0; ii < 10; ii ++) {
            Image halfStar = new Image();
            if (_handler != null) {
                RatingMouseHandler rhandler = new RatingMouseHandler(ii/2+1);
                halfStar.addMouseUpHandler(rhandler);
                halfStar.addMouseOutHandler(rhandler);
                halfStar.addMouseMoveHandler(rhandler);
            }
            add(halfStar);
        }

        // finally set our rating and set ourselves up
        setRating(rating);
    }

    public void setRating (float rating)
    {
        rating = Math.max(0, Math.min(5, rating));
        updateStarImage(_rating = rating);
    }

    public float getRating ()
    {
        return _rating;
    }

    protected void updateStarImage (float rating)
    {
        int filledStars = (int) (rating * 2);
        String prefix = "/images/ui/stars/" + (_halfSize ? "half" : "full") + "/";
        String fullPrefix = prefix + _starType + "_";
        for (int ii = 0; ii < filledStars; ii++) {
            String path = fullPrefix + ((ii % 2) == 0 ? "lhalf" : "rhalf") + ".png";
            ((Image) getWidget(ii)).setUrl(path);
        }
        for (int ii = filledStars; ii < 10; ii++) {
            String path = prefix + "empty_" + ((ii % 2) == 0 ? "lhalf" : "rhalf") + ".png";
            ((Image) getWidget(ii)).setUrl(path);
        }
    }

    // each half star gets a listener that knows which rating it translates to
    protected class RatingMouseHandler
        implements MouseOutHandler, MouseMoveHandler, MouseUpHandler
    {
        public RatingMouseHandler (int starIx)
        {
            _ratingInterval = starIx;
        }

        // from interface MouseListener
        public void onMouseOut (MouseOutEvent evnt)
        {
            updateStarImage(_rating);
            _handler.starMouseOff();
        }

        // from interface MouseListener
        public void onMouseMove (MouseMoveEvent event)
        {
            updateStarImage(_ratingInterval);
            _handler.starMouseOn((byte) _ratingInterval);
        }

        // from interface MouseListener
        public void onMouseUp (MouseUpEvent event)
        {
            _handler.starClicked((byte) _ratingInterval);
        }

        protected int _ratingInterval;
    }

    /** The rating we're displaying when the mouse is not hovering over us. */
    protected float _rating;

    /** Are we in half-size mode? */
    protected boolean _halfSize;

    /** The type of star images we use: "average" or "user". */
    protected String _starType;

    /** Called when a star is clicked or moused over, may be null. */
    protected StarMouseListener _handler;
}
