define(function(require) {

  var $ = require('jquery'),
      React = require('react'),
      serializeForm = require('../../mixins/serialize-form'),
      Fluxbone = require('../../mixins/fluxbone'),
      toggleForm = require('../../mixins/toggle-form'),
      RequestStatus = require('../../mixins/request-status'),
      PackageComponent = require('./package-component'),
      SotaDispatcher = require('sota-dispatcher');

  var AddPackageComponent = React.createClass({
    mixins: [
      toggleForm,
      RequestStatus.Mixin("PackageStore")
    ],
    handleSubmit: function(e) {
      e.preventDefault();

      var payload = serializeForm(this.refs.form);

      var file = $('.file-upload')[0].files[0];
      payload.file = file;

      SotaDispatcher.dispatch({
        actionType: 'package-add',
        package: payload
      });
    },
    buttonLabel: "NEW PACKAGE",
    form: function() {
      return (
        <div>
          <form ref='form' onSubmit={this.handleSubmit} encType="multipart/form-data">
            <div className="form-group">
              <label htmlFor="name">Package Name</label>
              <input type="text" className="form-control" name="name" ref="name" placeholder="Package Name"/>
            </div>
            <div className="form-group">
              <label htmlFor="version">Version</label>
              <input type="text" className="form-control" name="version" ref="version" placeholder="10"/>
            </div>
            <div className="form-group">
              <label htmlFor="description">Description</label>
              <input type="text" className="form-control" name="description" ref="description" placeholder="Description text"/>
            </div>
            <div className="form-group">
              <label htmlFor="vendor">Vendor</label>
              <input type="text" className="form-control" name="vendor" ref="vendor" placeholder="Vendor name"/>
            </div>
            <div className="form-group">
              <label htmlFor="binary">Package Binary</label>
              <input type="file" className="file-upload" name="file" />
            </div>
            <div className="form-group">
              <button type="submit" className="btn btn-primary">Add PACKAGE</button>
            </div>
            <div className="form-group">
              { this.state.postStatus }
            </div>
          </form>
        </div>
      );
    }
  });

  return AddPackageComponent;
});