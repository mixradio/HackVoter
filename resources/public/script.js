if((typeof(allowvoting) !== 'undefined') && allowvoting) {
  var votes = null;
  var votesused = 0;
  var flashing = false;
  var floaterorigcolour = $.Color($('#uservotefloater'),'background-color');

  $.get("/votes").done(function(data) {
    votes = data;
    updatevotes();
  });

  function updatevotes() {
    votesused = 0;
    // sum up votes used...
    for(var i=0;i<votes.length;i++) {
      var id = votes[i].id;
      var votecount = votes[i].uservotes;
      votesused += votecount;
      $('#' + id).find(".uservote").html(votecount);
      var votedown = $('#' + id).find(".votebtndown");
      if(votecount == 0)
        votedown.prop('disabled', true);
      else
        votedown.prop('disabled', false);
    }

    // update votes used...
    updatevotefloater(votesused);

    // now enable / disable vote up buttons based on votes left:
    for(var i=0;i<votes.length;i++) {
      var id = votes[i].id;
      var voteup = $('#' + id).find(".votebtnup");
      if(votesused == allocation)
        voteup.prop('disabled', true);
      else
        voteup.prop('disabled', false);
    }
  }

  function vote(id, vote) {
    var newtotalvotes = votesused + vote;
    if(newtotalvotes > allocation) {
      flashvotefloater();
      return;
    }
    for (var i=0;i<votes.length;i++) {
      var voteitem = votes[i];
      if(id == voteitem.id) {
        var newvote = voteitem.uservotes + vote;
        if(newvote < 0) {
          newvote = 0;
        }
        else if(newvote > maxspend) {
          newvote = maxspend;
          flashvotefloater();
        }

        if(voteitem.uservotes != newvote) {
          voteitem.uservotes = newvote;
          $.post('/hacks/' + id + '/votes', {"votes": newvote},function(data) {
            votes = data;
            updatevotes();
          });
        }
        break;
      }
    }
    updatevotes();
  }

  function flashvotefloater() {
    if(flashing)
      return;
    flashing = true;
    var floater = $('#uservotefloater');
    floater.animate({backgroundColor: '#ff2e80'}, 100).animate({backgroundColor: floaterorigcolour}, 200);
    flashing = false;
  }

  function updatevotefloater(votesused) {
    if(allowvoting) {
      var summary = (votesused == allocation ? "all" : votesused) + " of your " + allocation + " " + currency;
      $('#uservotefloater').html("You have spent " + summary + "<br/>You can use up to " + maxspend + " " + currency + " per hack");
      $('#uservotefloater').show();
    }
  }
}

function confirmdelete(title, deleteuri) {
  if(confirm('Are you sure you want to delete "' + title + '"?')) {
    window.location=deleteuri;
  }
}

function newimageuploaded(url) {
  $('#imgurl').val(url);
  $('#editpreview').attr("src",url);
  var msg = $('#noimg');
  if(msg) msg.hide();
  $('.message').html('Don\'t forget to save your changes');
}