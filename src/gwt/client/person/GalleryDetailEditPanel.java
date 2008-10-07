//
//$Id$

package client.person;

import client.dnd.PayloadWidget;
import client.ui.CreatorLabel;
import client.ui.LimitedTextArea;
import client.ui.MsoyUI;
import client.util.MediaUtil;

import com.allen_sauer.gwt.dnd.client.DragContext;
import com.allen_sauer.gwt.dnd.client.PickupDragController;
import com.allen_sauer.gwt.dnd.client.VetoDragException;
import com.allen_sauer.gwt.dnd.client.drop.SimpleDropController;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.FocusListenerAdapter;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

import com.threerings.gwt.ui.CenteredBox;
import com.threerings.msoy.data.all.MediaDesc;
import com.threerings.msoy.item.data.all.Photo;
import com.threerings.msoy.person.gwt.Gallery;
import com.threerings.msoy.person.gwt.GalleryData;

/**
* Displays the Gallery meta data for edit.
*
* @author mjensen
*/
public class GalleryDetailEditPanel extends AbsolutePanel
{
    public GalleryDetailEditPanel (GalleryData galleryData, PickupDragController dragController)
    {
        final Gallery gallery = galleryData.gallery;
        setStyleName("galleryDetailPanel");

        Widget thumbnail;
        if (gallery.thumbMedia != null) {
            thumbnail = MediaUtil.createMediaView(gallery.thumbMedia, MediaDesc.THUMBNAIL_SIZE);
        } else {
            thumbnail = MsoyUI.createLabel(_pmsgs.galleryNoThumbnail(), "Text");
        }

        final CenteredBox thumbnailPanel = new CenteredBox(thumbnail, "GalleryThumbnail",
            MediaDesc.getWidth(MediaDesc.THUMBNAIL_SIZE),
            MediaDesc.getHeight(MediaDesc.THUMBNAIL_SIZE));
        //final SimplePanel thumbnailPanel = MsoyUI.createSimplePanel("GalleryThumbnail", thumbnail);
        // allow for the user to drop an image on the thumbnail panel to set the thumbnail media
        // for the gallery
        SimpleDropController thumbnailDrop = new SimpleDropController(thumbnailPanel) {
            @Override public void onPreviewDrop(DragContext context) throws VetoDragException {
                if (context.draggable instanceof PayloadWidget<?>) {
                    PayloadWidget<?> droppings = (PayloadWidget<?>) context.draggable;
                    if (droppings.getPayload() instanceof Photo) {
                        Photo image = (Photo) droppings.getPayload();
                        gallery.thumbMedia = image.thumbMedia;
                        thumbnailPanel.clear();
                        thumbnailPanel.add(MediaUtil.createMediaView(image.thumbMedia,
                            MediaDesc.THUMBNAIL_SIZE));
                    }
                }
                // we've extracted all the information we need. return the dropped widget to whence
                // it came
                throw new VetoDragException();
            }
        };
        dragController.registerDropController(thumbnailDrop);
        add(thumbnailPanel, 10, 10);

        add(_countLabel = MsoyUI.createLabel("", "Count"), 20, 80);
        setCount(galleryData.photos.size());

        // do not allow profile gallery name to be edited
        if (gallery.isProfileGallery()) {
            add(MsoyUI.createLabel(GalleryPanel.getGalleryLabel(gallery), "Name"), 105, 10);
        } else {
            TextBox name = MsoyUI.createTextBox(gallery.name, Gallery.MAX_NAME_LENGTH,
                Gallery.MAX_NAME_LENGTH);
            name.addStyleName("Name");
            name.addChangeListener(new ChangeListener() {
                public void onChange (Widget sender) {
                    gallery.name = ((TextBox) sender).getText();
                }
            });
            add(name, 105, 7);
        }

        // creator name
        add(new CreatorLabel(galleryData.owner), 415, 10);

        // description textarea
        final LimitedTextArea description = new LimitedTextArea(Gallery.MAX_DESCRIPTION_LENGTH, 20,
            10);
        description.setText(gallery.description != null && gallery.description.length() > 0
            ? gallery.description : _pmsgs.galleryDescDefault());
        description.addStyleName("Description");
        description.getTextArea().addChangeListener(new ChangeListener() {
            public void onChange (Widget sender) {
                gallery.description = description.getText();
            }
        });
        description.getTextArea().addFocusListener(new FocusListenerAdapter() {
            public void onFocus (Widget sender) {
                if (description.getTextArea().getText().equals(_pmsgs.galleryDescDefault())) {
                    description.getTextArea().setText("");
                }
            }
        });
        add(description, 100, 35);
    }

    /**
     * Updates the count label with the current number of gallery photos.
     */
    public void setCount (int count)
    {
        String text = count == 1 ? _pmsgs.galleryOnePhoto() : _pmsgs.galleryPhotoCount(""+count);
        _countLabel.setText(text);
    }

    protected Label _countLabel;

    protected static final PersonMessages _pmsgs = (PersonMessages)GWT.create(PersonMessages.class);
}

