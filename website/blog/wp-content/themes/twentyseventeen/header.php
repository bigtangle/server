<?php
/**
 * The header for our theme
 *
 * This is the template that displays all of the <head> section and everything up until <div id="content">
 *
 * @link https://developer.wordpress.org/themes/basics/template-files/#template-partials
 *
 * @package WordPress
 * @subpackage Twenty_Seventeen
 * @since 1.0
 * @version 1.0
 */

?><!DOCTYPE html>
<html lang="en">

  <head>

    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
    <meta name="description" content="">
    <meta name="author" content="">

    <title>BigTangle - The fast, secure and efficient way to transfer cash.</title>

	<?php wp_head(); ?>
    <!-- Bootstrap core CSS -->
	<link href="//bigtangle.net/vendor/bootstrap/css/bootstrap.min.css" rel="stylesheet">

    <!-- Custom styles for this template -->
    <link href="//bigtangle.net/css/scrolling-nav.css" rel="stylesheet">
	
	<link rel="icon" type="image/png" href="//bigtangle.net/favicon.png" />

  </head>

  <body id="page-top" <?php body_class(); ?>>
	<div id="particles-js"></div>
    <!-- Navigation -->
    <nav class="navbar navbar-expand-lg navbar-dark bg-dark fixed-top" id="mainNav">
      <div class="container">
        <a class="navbar-brand js-scroll-trigger" href="./"><img src="//bigtangle.net/img/whitelogo.png" width="30" /> BigTangle | Blog</a>
        <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarResponsive" aria-controls="navbarResponsive" aria-expanded="false" aria-label="Toggle navigation">
          <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="navbarResponsive">
          <ul class="navbar-nav ml-auto">
            <li class="nav-item">
              <a class="nav-link " href="//bigtangle.net/whitepaper.pdf" target="_blank">Whitepaper</a>
            </li>
            <li class="nav-item">
              <a class="nav-link " href="//bigtangle.net">Home</a>
            </li>
            <li class="nav-item">
              <a class="nav-link active text-white" href="./">Blog</a>
            </li>
            <li class="nav-item">
              <a class="nav-link" href="//bigtangle.net/imprint.html">Imprint</a>
            </li>
          </ul>
        </div>
      </div>
    </nav>
	
	<section id="blog">
      <div class="container">
        <div class="row">
          <div class="col-lg-8 mx-auto">
			<?php if(is_front_page()):?>
				<h2 style="margin-bottom: 20px">Recent articles</h2>
			<?php endif; ?>
            <div class="row">